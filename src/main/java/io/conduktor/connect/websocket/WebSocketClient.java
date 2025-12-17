package io.conduktor.connect.websocket;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket client that handles connection, reconnection, and message buffering.
 */
public class WebSocketClient extends WebSocketListener {
    private static final Logger log = LoggerFactory.getLogger(WebSocketClient.class);

    private final String url;
    private final String subscriptionMessage;
    private final boolean reconnectEnabled;
    private final long reconnectIntervalMs;
    private final Map<String, String> headers;
    private final int queueSize;
    private final long connectionTimeoutMs;

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private final LinkedBlockingDeque<String> messageQueue;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong reconnectAttempts = new AtomicLong(0);

    public WebSocketClient(
            String url,
            String subscriptionMessage,
            boolean reconnectEnabled,
            long reconnectIntervalMs,
            Map<String, String> headers,
            int queueSize,
            long connectionTimeoutMs
    ) {
        this.url = url;
        this.subscriptionMessage = subscriptionMessage;
        this.reconnectEnabled = reconnectEnabled;
        this.reconnectIntervalMs = reconnectIntervalMs;
        this.headers = headers != null ? headers : new HashMap<>();
        this.queueSize = queueSize;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.messageQueue = new LinkedBlockingDeque<>(queueSize);
    }

    /**
     * Start the WebSocket connection.
     */
    public void start() {
        log.info("Starting WebSocket client for URL: {}", url);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // No read timeout for streaming
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive
                .build();

        connect();
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
        log.info("Stopping WebSocket client");
        shouldReconnect.set(false);

        if (webSocket != null) {
            webSocket.close(1000, "Connector shutdown");
        }

        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }

        connected.set(false);
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

    // WebSocketListener callbacks

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        log.info("WebSocket connection opened to {}", url);
        connected.set(true);
        reconnectAttempts.set(0);

        // Send subscription message if configured
        if (subscriptionMessage != null && !subscriptionMessage.isEmpty()) {
            log.info("Sending subscription message: {}", subscriptionMessage);
            webSocket.send(subscriptionMessage);
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        messagesReceived.incrementAndGet();

        // Add message to queue
        boolean added = messageQueue.offer(text);
        if (!added) {
            log.warn("Message queue is full (size: {}), dropping message", queueSize);
        }

        if (log.isDebugEnabled() && messagesReceived.get() % 100 == 0) {
            log.debug("Received {} messages, queue size: {}", messagesReceived.get(), messageQueue.size());
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket closing: code={}, reason={}", code, reason);
        connected.set(false);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket closed: code={}, reason={}", code, reason);
        connected.set(false);
        attemptReconnect();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.error("WebSocket connection failed: {}", t.getMessage(), t);
        connected.set(false);
        attemptReconnect();
    }

    /**
     * Attempt to reconnect if enabled.
     */
    private void attemptReconnect() {
        if (!reconnectEnabled || !shouldReconnect.get()) {
            log.info("Reconnection disabled or shutdown requested, not reconnecting");
            return;
        }

        reconnectAttempts.incrementAndGet();
        log.info("Attempting reconnection #{} in {} ms", reconnectAttempts.get(), reconnectIntervalMs);

        try {
            Thread.sleep(reconnectIntervalMs);
            if (shouldReconnect.get()) {
                connect();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Reconnection interrupted");
        }
    }
}
