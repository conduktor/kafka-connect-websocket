package io.conduktor.connect.websocket;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket Source Task that polls messages from WebSocket and produces them to Kafka.
 */
public class WebSocketSourceTask extends SourceTask {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSourceTask.class);

    private WebSocketClient client;
    private String kafkaTopic;
    private WebSocketSourceConnectorConfig config;
    private final AtomicLong recordsProduced = new AtomicLong(0);
    private long lastLogTime = System.currentTimeMillis();

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
        log.info("Starting WebSocket Source Task");

        config = new WebSocketSourceConnectorConfig(props);
        kafkaTopic = config.getKafkaTopic();

        // Parse headers
        Map<String, String> headers = parseHeaders(config.getHeaders());

        // Add auth token if provided
        String authToken = config.getAuthToken();
        if (authToken != null && !authToken.isEmpty()) {
            headers.put("Authorization", "Bearer " + authToken);
        }

        // Create and start WebSocket client
        client = new WebSocketClient(
                config.getWebSocketUrl(),
                config.getSubscriptionMessage(),
                config.isReconnectEnabled(),
                config.getReconnectIntervalMs(),
                headers,
                config.getMessageQueueSize(),
                config.getConnectionTimeoutMs()
        );

        client.start();
        log.info("WebSocket Source Task started successfully");
    }

    @Override
    public List<SourceRecord> poll() throws InterruptedException {
        // Get messages from the WebSocket client
        List<String> messages = client.getMessages();

        if (messages.isEmpty()) {
            // No messages available, wait a bit
            Thread.sleep(100);
            return null;
        }

        // Convert messages to SourceRecords
        List<SourceRecord> records = new ArrayList<>(messages.size());
        for (String message : messages) {
            SourceRecord record = createSourceRecord(message);
            if (record != null) {
                records.add(record);
                recordsProduced.incrementAndGet();
            }
        }

        // Log metrics periodically
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 30000) { // Every 30 seconds
            logMetrics();
            lastLogTime = now;
        }

        return records;
    }

    @Override
    public void stop() {
        log.info("Stopping WebSocket Source Task");

        if (client != null) {
            client.stop();
        }

        logMetrics();
        log.info("WebSocket Source Task stopped");
    }

    /**
     * Create a SourceRecord from a WebSocket message.
     */
    private SourceRecord createSourceRecord(String message) {
        try {
            // Source partition and offset (not really used for WebSocket, but required by Kafka Connect)
            Map<String, Object> sourcePartition = Collections.singletonMap("websocket_url", config.getWebSocketUrl());
            Map<String, Object> sourceOffset = Collections.singletonMap("timestamp", System.currentTimeMillis());

            return new SourceRecord(
                    sourcePartition,
                    sourceOffset,
                    kafkaTopic,
                    null, // partition
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
     * Parse headers from configuration string.
     * Format: key1:value1,key2:value2
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
                headers.put(keyValue[0].trim(), keyValue[1].trim());
            } else {
                log.warn("Invalid header format: {}", pair);
            }
        }

        return headers;
    }

    /**
     * Log metrics about the task's performance.
     */
    private void logMetrics() {
        boolean isConnected = client != null && client.isConnected();
        long messagesReceived = client != null ? client.getMessagesReceived() : 0;
        long reconnectAttempts = client != null ? client.getReconnectAttempts() : 0;

        if (isConnected) {
            log.info("WebSocket Task Metrics - Connected: true, Messages Received: {}, Records Produced: {}, Reconnect Attempts: {}",
                    messagesReceived, recordsProduced.get(), reconnectAttempts);
        } else {
            log.warn("WebSocket Task Metrics - Connected: false, Messages Received: {}, Records Produced: {}, Reconnect Attempts: {}",
                    messagesReceived, recordsProduced.get(), reconnectAttempts);
        }
    }
}
