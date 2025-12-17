package io.conduktor.connect.websocket;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket client that handles connection, reconnection, and message buffering.
 */
public class WebSocketClient extends WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);
    private static final double QUEUE_WARNING_THRESHOLD = 0.80; // 80% threshold

    private final String url;
    private final String subscriptionMessage;
    private final boolean reconnectEnabled;
    private final long reconnectIntervalMs;
    private final int maxReconnectAttempts;
    private final long maxBackoffMs;
    private final Map<String, String> headers;
    private final int queueSize;
    private final long connectionTimeoutMs;

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private final LinkedBlockingDeque<String> messageQueue;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesDropped = new AtomicLong(0);
    private final AtomicLong reconnectAttempts = new AtomicLong(0);
    private volatile long lastMessageTimestamp = 0;
    private volatile boolean queueWarningLogged = false;
    private ScheduledExecutorService reconnectExecutor;
    private ScheduledFuture<?> reconnectTask;
    private WebSocketMetrics metrics;

    public WebSocketClient(
            String url,
            String subscriptionMessage,
            boolean reconnectEnabled,
            long reconnectIntervalMs,
            int maxReconnectAttempts,
            long maxBackoffMs,
            Map<String, String> headers,
            int queueSize,
            long connectionTimeoutMs
    ) {
        this.url = url;
        this.subscriptionMessage = subscriptionMessage;
        this.reconnectEnabled = reconnectEnabled;
        this.reconnectIntervalMs = reconnectIntervalMs;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.maxBackoffMs = maxBackoffMs;
        this.headers = headers != null ? headers : new HashMap<>();
        this.queueSize = queueSize;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.messageQueue = new LinkedBlockingDeque<>(queueSize);
    }

    /**
     * Start the WebSocket connection.
     */
    public void start() {
        MDC.put("websocket_url", url);
        log.info("event=websocket_client_starting url={} queue_capacity={} connection_timeout_ms={} reconnect_enabled={}",
                 url, queueSize, connectionTimeoutMs, reconnectEnabled);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for streaming
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
                .build();

        // Initialize reconnect executor with a single thread
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "websocket-reconnect");
            thread.setDaemon(true);
            return thread;
        });

        if (metrics != null) {
            metrics.setQueueCapacity(queueSize);
        }

        connect();
        log.info("event=websocket_client_started url={}", url);
        MDC.clear();
    }

    /**
     * Connect to the WebSocket endpoint.
     */
    private void connect() {
        Request.Builder requestBuilder = new Request.Builder().url(url);

        // Add custom headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }

        Request request = requestBuilder.build();
        webSocket = httpClient.newWebSocket(request, this);
    }

    /**
     * Stop the WebSocket connection.
     */
    public void stop() {
        MDC.put("websocket_url", url);
        log.info("event=websocket_client_stopping url={}", url);
        shouldReconnect.set(false);

        // Cancel any pending reconnection task
        if (reconnectTask != null && !reconnectTask.isDone()) {
            reconnectTask.cancel(false);
            log.debug("event=reconnect_task_cancelled");
        }

        // Shutdown reconnect executor
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                if (!reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("event=executor_shutdown_timeout executor=reconnect action=forcing_shutdown");
                    reconnectExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("event=executor_shutdown_interrupted executor=reconnect");
                reconnectExecutor.shutdownNow();
            }
        }

        // Close WebSocket connection
        if (webSocket != null) {
            webSocket.close(1000, "Connector shutdown");
        }

        // Shutdown HTTP client resources
        if (httpClient != null) {
            ExecutorService dispatcherExecutor = httpClient.dispatcher().executorService();
            dispatcherExecutor.shutdown();
            try {
                if (!dispatcherExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("event=executor_shutdown_timeout executor=dispatcher action=forcing_shutdown");
                    dispatcherExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("event=executor_shutdown_interrupted executor=dispatcher");
                dispatcherExecutor.shutdownNow();
            }

            // Evict all connections from the pool
            httpClient.connectionPool().evictAll();
        }

        connected.set(false);
        log.info("event=websocket_client_stopped url={} messages_received={} messages_dropped={} reconnect_attempts={}",
                 url, messagesReceived.get(), messagesDropped.get(), reconnectAttempts.get());
        MDC.clear();
    }

    /**
     * Get available messages from the queue.
     */
    public List<String> getMessages() {
        List<String> messages = new ArrayList<>();
        messageQueue.drainTo(messages);
        return messages;
    }

    /**
     * Check if the client is connected.
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Get the number of messages received.
     */
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    /**
     * Get the number of reconnection attempts.
     */
    public long getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    /**
     * Get the number of messages dropped.
     */
    public long getMessagesDropped() {
        return messagesDropped.get();
    }

    /**
     * Get the current queue size.
     */
    public int getQueueSize() {
        return messageQueue.size();
    }

    /**
     * Get the queue capacity.
     */
    public int getQueueCapacity() {
        return queueSize;
    }

    /**
     * Get the queue utilization as a percentage.
     */
    public double getQueueUtilization() {
        return (messageQueue.size() * 100.0) / queueSize;
    }

    /**
     * Get milliseconds since last message received.
     */
    public long getMillisSinceLastMessage() {
        if (lastMessageTimestamp == 0) return -1;
        return System.currentTimeMillis() - lastMessageTimestamp;
    }

    /**
     * Set the metrics tracker for this client.
     */
    public void setMetrics(WebSocketMetrics metrics) {
        this.metrics = metrics;
    }

    // WebSocketListener callbacks

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        MDC.put("websocket_url", url);
        log.info("event=websocket_opened url={} response_code={}", url, response.code());
        connected.set(true);
        reconnectAttempts.set(0);
        queueWarningLogged = false; // Reset warning flag on new connection

        if (metrics != null) {
            metrics.setConnected(true);
        }

        // Send subscription message if configured
        if (subscriptionMessage != null && !subscriptionMessage.isEmpty()) {
            // Redact sensitive data in logs
            String redactedMessage = redactSensitiveData(subscriptionMessage);
            log.info("event=subscription_sent message_preview={}", redactedMessage);
            webSocket.send(subscriptionMessage);
        }
        MDC.clear();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        messagesReceived.incrementAndGet();
        lastMessageTimestamp = System.currentTimeMillis();

        if (metrics != null) {
            metrics.incrementMessagesReceived();
        }

        // Check queue utilization and log warnings
        int currentSize = messageQueue.size();
        double utilization = (currentSize * 100.0) / queueSize;

        // Add message to queue
        boolean added = messageQueue.offer(text);
        if (!added) {
            messagesDropped.incrementAndGet();
            if (metrics != null) {
                metrics.incrementMessagesDropped();
            }
            MDC.put("websocket_url", url);
            log.warn("event=message_dropped reason=queue_full queue_size={} queue_capacity={} utilization_percent=100.0 messages_dropped_total={}",
                     queueSize, queueSize, messagesDropped.get());
            MDC.clear();
        } else {
            // Update metrics
            if (metrics != null) {
                metrics.updateQueueSize(messageQueue.size());
            }

            // Log warning when queue is at 80% capacity
            if (utilization >= (QUEUE_WARNING_THRESHOLD * 100) && !queueWarningLogged) {
                MDC.put("websocket_url", url);
                log.warn("event=queue_high_utilization queue_size={} queue_capacity={} utilization_percent={} threshold_percent={}",
                         currentSize, queueSize, String.format("%.2f", utilization), (int)(QUEUE_WARNING_THRESHOLD * 100));
                queueWarningLogged = true;
                MDC.clear();
            } else if (utilization < (QUEUE_WARNING_THRESHOLD * 100)) {
                // Reset warning flag when utilization drops below threshold
                queueWarningLogged = false;
            }
        }

        // Log metrics periodically
        if (log.isDebugEnabled() && messagesReceived.get() % 100 == 0) {
            MDC.put("websocket_url", url);
            log.debug("event=messages_received_milestone messages_received={} queue_size={} queue_utilization_percent={}",
                      messagesReceived.get(), messageQueue.size(), String.format("%.2f", utilization));
            MDC.clear();
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        MDC.put("websocket_url", url);
        log.info("event=websocket_closing code={} reason={}", code, reason);
        connected.set(false);
        if (metrics != null) {
            metrics.setConnected(false);
        }
        MDC.clear();
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        MDC.put("websocket_url", url);
        log.info("event=websocket_closed code={} reason={}", code, reason);
        connected.set(false);
        if (metrics != null) {
            metrics.setConnected(false);
        }
        MDC.clear();
        attemptReconnect();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        MDC.put("websocket_url", url);
        String responseCode = response != null ? String.valueOf(response.code()) : "N/A";
        log.error("event=websocket_failure error_message={} response_code={}", t.getMessage(), responseCode, t);
        connected.set(false);
        if (metrics != null) {
            metrics.setConnected(false);
        }
        MDC.clear();
        attemptReconnect();
    }

    /**
     * Attempt to reconnect if enabled.
     * Uses exponential backoff and prevents concurrent reconnection attempts.
     */
    private void attemptReconnect() {
        if (!reconnectEnabled || !shouldReconnect.get()) {
            MDC.put("websocket_url", url);
            log.info("event=reconnect_skipped reason=disabled_or_shutdown");
            MDC.clear();
            return;
        }

        // Prevent concurrent reconnection attempts
        if (!reconnecting.compareAndSet(false, true)) {
            MDC.put("websocket_url", url);
            log.debug("event=reconnect_skipped reason=already_in_progress");
            MDC.clear();
            return;
        }

        long currentAttempt = reconnectAttempts.incrementAndGet();
        if (metrics != null) {
            metrics.incrementReconnects();
        }

        // Check max retry limit (-1 means infinite)
        if (maxReconnectAttempts > 0 && currentAttempt > maxReconnectAttempts) {
            MDC.put("websocket_url", url);
            log.error("event=reconnect_failed reason=max_attempts_reached max_attempts={} current_attempt={}",
                      maxReconnectAttempts, currentAttempt);
            MDC.clear();
            reconnecting.set(false);
            shouldReconnect.set(false);
            return;
        }

        // Calculate exponential backoff delay
        long backoffDelay = calculateBackoffDelay(currentAttempt);
        MDC.put("websocket_url", url);
        log.info("event=reconnect_scheduled attempt={} backoff_ms={}", currentAttempt, backoffDelay);
        MDC.clear();

        // Schedule reconnection on dedicated executor (off callback thread)
        reconnectTask = reconnectExecutor.schedule(() -> {
            try {
                if (shouldReconnect.get()) {
                    MDC.put("websocket_url", url);
                    log.info("event=reconnect_executing attempt={}", currentAttempt);
                    MDC.clear();
                    connect();
                }
            } catch (Exception e) {
                MDC.put("websocket_url", url);
                log.error("event=reconnect_error attempt={} error_message={}", currentAttempt, e.getMessage(), e);
                MDC.clear();
            } finally {
                reconnecting.set(false);
            }
        }, backoffDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculate exponential backoff delay with jitter.
     * Formula: min(reconnectIntervalMs * 2^(attempt-1), maxBackoffMs) + random jitter
     */
    private long calculateBackoffDelay(long attempt) {
        // Calculate exponential backoff: baseDelay * 2^(attempt-1)
        long exponentialDelay = reconnectIntervalMs * (long) Math.pow(2, attempt - 1);

        // Cap at maximum backoff
        long cappedDelay = Math.min(exponentialDelay, maxBackoffMs);

        // Add random jitter (0-25% of the delay) to avoid thundering herd
        long jitter = (long) (cappedDelay * 0.25 * Math.random());

        return cappedDelay + jitter;
    }

    /**
     * Redact sensitive data from messages for logging.
     * Redacts values for common auth-related keys.
     */
    private String redactSensitiveData(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // Limit length and redact common sensitive patterns
        String preview = message.length() > 100 ? message.substring(0, 100) + "..." : message;

        // Redact common sensitive fields (case-insensitive)
        preview = preview.replaceAll("(?i)(\"(?:token|key|secret|password|auth|api[_-]?key)\"\\s*:\\s*\")([^\"]+)(\")", "$1***REDACTED***$3");
        preview = preview.replaceAll("(?i)(Bearer\\s+)[^\\s\"]+", "$1***REDACTED***");

        return preview;
    }
}
