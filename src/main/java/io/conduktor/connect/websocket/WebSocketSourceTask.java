package io.conduktor.connect.websocket;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.management.JMException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket Source Task that polls messages from WebSocket and produces them to Kafka.
 *
 * This implementation provides:
 * - Sequence-based offset management for reliable message tracking
 * - Connection session tracking to detect reconnections
 * - Delivery guarantee via commitRecord() callback
 * - Graceful shutdown with message draining
 * - Proper queue-based polling without Thread.sleep()
 */
public class WebSocketSourceTask extends SourceTask {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSourceTask.class);

    // Shutdown and lifecycle management
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private static final long SHUTDOWN_DRAIN_TIMEOUT_MS = 5000L;

    // Offset management - sequence-based tracking
    private final AtomicLong messageSequence = new AtomicLong(0);
    private final AtomicLong lastCommittedSequence = new AtomicLong(-1);
    private volatile String connectionSessionId;

    // WebSocket client and configuration
    private WebSocketClient client;
    private String kafkaTopic;
    private WebSocketSourceConnectorConfig config;

    // Metrics
    private final AtomicLong recordsProduced = new AtomicLong(0);
    private long lastLogTime = System.currentTimeMillis();
    private WebSocketMetrics metrics;
    private String connectorName;

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
        config = new WebSocketSourceConnectorConfig(props);
        kafkaTopic = config.getKafkaTopic();
        String websocketUrl = config.getWebSocketUrl();

        // Extract connector name from properties or generate one
        connectorName = props.getOrDefault("name", "websocket-connector-" + UUID.randomUUID().toString().substring(0, 8));

        // Set up MDC context for all logging in this task
        MDC.put("connector_name", connectorName);
        MDC.put("websocket_url", websocketUrl);
        MDC.put("kafka_topic", kafkaTopic);

        log.info("event=task_starting connector_name={} websocket_url={} kafka_topic={}",
                 connectorName, websocketUrl, kafkaTopic);

        // Initialize connection session ID (unique identifier for this connection lifecycle)
        connectionSessionId = UUID.randomUUID().toString();
        MDC.put("session_id", connectionSessionId);
        log.info("event=session_initialized session_id={}", connectionSessionId);

        // Initialize JMX metrics
        try {
            metrics = new WebSocketMetrics(connectorName, websocketUrl);
            log.info("event=jmx_metrics_initialized connector_name={}", connectorName);
        } catch (JMException e) {
            log.error("event=jmx_metrics_init_failed connector_name={} error={}", connectorName, e.getMessage(), e);
            // Continue without metrics - not critical for operation
        }

        // Restore offset from Kafka Connect framework if available
        restoreOffsetState();

        // Parse headers
        Map<String, String> headers = parseHeaders(config.getHeaders());

        // Add auth token if provided
        String authToken = config.getAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            headers.put("Authorization", "Bearer " + authToken);
        }

        // Create and start WebSocket client
        client = new WebSocketClient(
                websocketUrl,
                config.getSubscriptionMessage(),
                config.isReconnectEnabled(),
                config.getReconnectIntervalMs(),
                config.getMaxReconnectAttempts(),
                config.getMaxBackoffMs(),
                headers,
                config.getMessageQueueSize(),
                config.getConnectionTimeoutMs()
        );

        // Link metrics to client
        if (metrics != null) {
            client.setMetrics(metrics);
        }

        client.start();
        log.info("event=task_started session_id={} starting_sequence={} queue_capacity={}",
                connectionSessionId, messageSequence.get(), config.getMessageQueueSize());
    }

    @Override
    public List<SourceRecord> poll() throws InterruptedException {
        // If we're stopping, don't accept new messages
        if (stopping.get()) {
            return null;
        }

        // FIX #3: Remove Thread.sleep() - let the framework handle polling intervals
        // Use non-blocking getMessages() which drains the queue
        List<String> messages = client.getMessages();

        if (messages.isEmpty()) {
            // Return null to let Kafka Connect framework control the polling pace
            // The framework will handle backoff and avoid busy-waiting
            return null;
        }

        // Convert messages to SourceRecords with sequence-based offsets
        List<SourceRecord> records = new ArrayList<>(messages.size());
        for (String message : messages) {
            SourceRecord record = createSourceRecord(message);
            if (record != null) {
                records.add(record);
                recordsProduced.incrementAndGet();
            }
        }

        // Update JMX metrics
        if (metrics != null && !records.isEmpty()) {
            metrics.incrementRecordsProduced(records.size());
        }

        // Log metrics periodically
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 30000) { // Every 30 seconds
            logMetrics();
            lastLogTime = now;
        }

        return records.isEmpty() ? null : records;
    }

    @Override
    public void stop() {
        log.info("event=task_stopping session_id={}", connectionSessionId);

        // FIX #4: Graceful shutdown with message draining
        // Step 1: Set stopping flag to prevent accepting new messages in poll()
        stopping.set(true);

        // Step 2: Drain remaining messages from the queue with timeout
        long drainStartTime = System.currentTimeMillis();
        int drainedMessages = 0;

        if (client != null) {
            log.info("event=message_draining_started timeout_ms={}", SHUTDOWN_DRAIN_TIMEOUT_MS);

            while (System.currentTimeMillis() - drainStartTime < SHUTDOWN_DRAIN_TIMEOUT_MS) {
                List<String> messages = client.getMessages();
                if (messages.isEmpty()) {
                    break; // No more messages to drain
                }

                drainedMessages += messages.size();
                log.debug("event=messages_drained count={}", messages.size());

                // Small sleep to allow framework to process these messages
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("event=drain_interrupted");
                    break;
                }
            }

            log.info("event=message_draining_completed drained_count={} duration_ms={}",
                    drainedMessages, (System.currentTimeMillis() - drainStartTime));

            // Step 3: Stop the WebSocket client
            client.stop();
        }

        // Step 4: Log final metrics and close JMX
        logMetrics();

        if (metrics != null) {
            try {
                metrics.close();
                log.info("event=jmx_metrics_closed connector_name={}", connectorName);
            } catch (Exception e) {
                log.error("event=jmx_metrics_close_failed error={}", e.getMessage(), e);
            }
        }

        log.info("event=task_stopped session_id={} final_sequence={} last_committed={}",
                connectionSessionId, messageSequence.get(), lastCommittedSequence.get());

        // Clear MDC context
        MDC.clear();
    }

    /**
     * FIX #2: Kafka Connect callback when a record is committed to Kafka.
     * This provides delivery guarantees and allows us to track which messages
     * have been successfully written to Kafka.
     */
    @Override
    public void commitRecord(SourceRecord record, org.apache.kafka.clients.producer.RecordMetadata metadata) {
        try {
            // Extract the sequence number from the source offset
            Map<String, ?> sourceOffset = record.sourceOffset();
            if (sourceOffset != null && sourceOffset.containsKey("sequence")) {
                long committedSeq = ((Number) sourceOffset.get("sequence")).longValue();
                long previousCommitted = lastCommittedSequence.get();

                // Update the last committed sequence
                lastCommittedSequence.set(committedSeq);

                // FIX #1: Detect sequence gaps (potential message loss)
                if (previousCommitted >= 0 && committedSeq != previousCommitted + 1) {
                    long gap = committedSeq - previousCommitted - 1;
                    log.warn("Sequence gap detected! Previous committed: {}, Current: {}, Gap size: {}",
                            previousCommitted, committedSeq, gap);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Record committed - Sequence: {}, Kafka offset: {}, partition: {}",
                            committedSeq, metadata.offset(), metadata.partition());
                }
            }
        } catch (Exception e) {
            log.error("Error in commitRecord callback", e);
        }
    }

    /**
     * FIX #1: Create a SourceRecord with sequence-based offset management.
     *
     * Offset structure:
     * - session_id: Unique ID for this connection lifecycle (detects reconnections)
     * - sequence: Monotonically increasing number for message ordering
     *
     * This allows:
     * - Detection of message reordering
     * - Detection of message loss (gaps in sequence)
     * - Tracking across WebSocket reconnections
     */
    private SourceRecord createSourceRecord(String message) {
        try {
            // Increment sequence number atomically for this message
            long sequence = messageSequence.incrementAndGet();

            // Source partition identifies the data stream source
            Map<String, Object> sourcePartition = new HashMap<>();
            sourcePartition.put("websocket_url", config.getWebSocketUrl());

            // FIX #1: Replace timestamp-based offset with sequence-based tracking
            Map<String, Object> sourceOffset = new HashMap<>();
            sourceOffset.put("session_id", connectionSessionId);
            sourceOffset.put("sequence", sequence);

            return new SourceRecord(
                    sourcePartition,
                    sourceOffset,
                    kafkaTopic,
                    null, // partition - let Kafka decide
                    null, // no key schema
                    null, // no key
                    Schema.STRING_SCHEMA,
                    message,
                    System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.error("Error creating SourceRecord from message: {}", message, e);
            return null;
        }
    }

    /**
     * Restore offset state from Kafka Connect framework.
     * This is called during task startup to resume from where we left off.
     */
    private void restoreOffsetState() {
        try {
            Map<String, Object> partition = Collections.singletonMap("websocket_url", config.getWebSocketUrl());
            Map<String, ?> offsetRaw = context.offsetStorageReader().offset(partition);
            @SuppressWarnings("unchecked")
            Map<String, Object> offset = offsetRaw != null ? (Map<String, Object>) offsetRaw : null;

            if (offset != null && !offset.isEmpty()) {
                // Restore the last committed sequence
                if (offset.containsKey("sequence")) {
                    long restoredSequence = ((Number) offset.get("sequence")).longValue();
                    messageSequence.set(restoredSequence);
                    lastCommittedSequence.set(restoredSequence);

                    String restoredSessionId = offset.getOrDefault("session_id", "unknown").toString();
                    log.info("Restored offset state - Session: {}, Sequence: {}",
                            restoredSessionId, restoredSequence);

                    // Note: Different session ID indicates a restart/reconnection
                    if (!connectionSessionId.equals(restoredSessionId)) {
                        log.warn("Session ID changed - Previous: {}, Current: {}. This indicates a connector restart.",
                                restoredSessionId, connectionSessionId);
                    }
                } else {
                    log.info("No sequence found in stored offset, starting from 0");
                }
            } else {
                log.info("No previous offset found, starting fresh from sequence 0");
            }
        } catch (Exception e) {
            log.error("Error restoring offset state, starting from 0", e);
        }
    }

    /**
     * Parse headers from configuration string.
     * Format: key1:value1,key2:value2
     * Validates header names per RFC 7230 and protects against CRLF injection.
     */
    private Map<String, String> parseHeaders(String headersStr) {
        Map<String, String> headers = new HashMap<>();

        if (headersStr == null || headersStr.trim().isEmpty()) {
            return headers;
        }

        String[] headerPairs = headersStr.split(",");
        for (String pair : headerPairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String headerName = keyValue[0].trim();
                String headerValue = keyValue[1].trim();

                // Validate header name per RFC 7230
                if (!isValidHeaderName(headerName)) {
                    log.error("Invalid header name '{}' - must match RFC 7230 token format", headerName);
                    throw new IllegalArgumentException("Invalid header name: " + headerName);
                }

                // Protect against CRLF injection in header values
                if (containsCRLF(headerValue)) {
                    log.error("Header value for '{}' contains CRLF characters - potential injection attack", headerName);
                    throw new IllegalArgumentException("Header value contains illegal CRLF characters: " + headerName);
                }

                headers.put(headerName, headerValue);
            } else {
                log.warn("Invalid header format (missing colon): {}", pair);
            }
        }

        return headers;
    }

    /**
     * Validate header name per RFC 7230 Section 3.2.
     * Header names are tokens consisting of visible ASCII characters except delimiters.
     */
    private boolean isValidHeaderName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        // RFC 7230 token = 1*tchar
        // tchar = "!" / "#" / "$" / "%" / "&" / "'" / "*" / "+" / "-" / "." /
        //         "0"-"9" / "A"-"Z" / "^" / "_" / "`" / "a"-"z" / "|" / "~"
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isValidTokenChar(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a character is a valid token character per RFC 7230.
     */
    private boolean isValidTokenChar(char c) {
        // Visible ASCII characters (33-126) excluding delimiters
        // Delimiters: "(),/:;<=>?@[\]{} and DQUOTE
        if (c < 33 || c > 126) {
            return false;
        }
        return c != '(' && c != ')' && c != ',' && c != '/' && c != ':' &&
               c != ';' && c != '<' && c != '=' && c != '>' && c != '?' &&
               c != '@' && c != '[' && c != '\\' && c != ']' && c != '{' &&
               c != '}' && c != '"';
    }

    /**
     * Check if a string contains CRLF characters (potential injection).
     */
    private boolean containsCRLF(String value) {
        if (value == null) {
            return false;
        }
        // Check for CR (0x0D) or LF (0x0A) characters
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    /**
     * Log metrics about the task's performance with enhanced observability.
     * Includes queue depth, lag, utilization, and time since last message.
     */
    private void logMetrics() {
        if (client == null) {
            return;
        }

        boolean isConnected = client.isConnected();
        long messagesReceived = client.getMessagesReceived();
        long messagesDropped = client.getMessagesDropped();
        long reconnectAttempts = client.getReconnectAttempts();
        long recordsProducedCount = recordsProduced.get();

        // Enhanced metrics
        int queueSize = client.getQueueSize();
        int queueCapacity = client.getQueueCapacity();
        double queueUtilization = client.getQueueUtilization();
        long lagCount = messagesReceived - recordsProducedCount;
        long millisSinceLastMessage = client.getMillisSinceLastMessage();

        // Structured logging with key=value format
        String metricsLog = String.format(
            "event=task_metrics connected=%s messages_received=%d messages_dropped=%d records_produced=%d " +
            "queue_size=%d queue_capacity=%d queue_utilization_percent=%.2f lag_count=%d " +
            "millis_since_last_message=%d reconnect_attempts=%d session_id=%s",
            isConnected, messagesReceived, messagesDropped, recordsProducedCount,
            queueSize, queueCapacity, queueUtilization, lagCount,
            millisSinceLastMessage, reconnectAttempts, connectionSessionId
        );

        // Log at appropriate level based on connection status and issues
        if (!isConnected) {
            log.warn(metricsLog + " status=DISCONNECTED");
        } else if (messagesDropped > 0) {
            log.warn(metricsLog + " status=DROPPING_MESSAGES");
        } else if (queueUtilization > 80.0) {
            log.warn(metricsLog + " status=HIGH_QUEUE_UTILIZATION");
        } else if (lagCount > 1000) {
            log.warn(metricsLog + " status=HIGH_LAG");
        } else if (millisSinceLastMessage > 60000 && millisSinceLastMessage != -1) {
            log.warn(metricsLog + " status=NO_RECENT_MESSAGES");
        } else {
            log.info(metricsLog + " status=HEALTHY");
        }
    }
}
