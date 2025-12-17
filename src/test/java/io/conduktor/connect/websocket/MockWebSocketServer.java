package io.conduktor.connect.websocket;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Mock WebSocket server for testing without external dependencies.
 * Addresses SME review finding: "HIGH: Tests depend on external service (echo.websocket.org)"
 *
 * Features:
 * - Controllable message sending
 * - Echo mode for testing subscription messages
 * - Configurable connection behavior (success, failure, delay)
 * - Message verification and assertion helpers
 */
public class MockWebSocketServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MockWebSocketServer.class);

    private final MockWebServer server;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private final List<String> messagesToSend = new ArrayList<>();
    private boolean echoMode = false;
    private boolean shouldAcceptConnection = true;
    private int connectionDelayMs = 0;
    private WebSocket activeWebSocket;

    public MockWebSocketServer() throws IOException {
        server = new MockWebServer();
        server.start();
        log.info("Mock WebSocket server started on {}", server.url("/"));
    }

    /**
     * Get the WebSocket URL for clients to connect to
     */
    public String getUrl() {
        return server.url("/").toString().replace("http://", "ws://");
    }

    /**
     * Get the port the server is listening on
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * Enable echo mode - server will echo back any received messages
     */
    public void setEchoMode(boolean enabled) {
        this.echoMode = enabled;
    }

    /**
     * Queue a message to be sent to connected clients
     */
    public void sendMessage(String message) {
        messagesToSend.add(message);
        if (activeWebSocket != null) {
            activeWebSocket.send(message);
        }
    }

    /**
     * Send multiple messages
     */
    public void sendMessages(String... messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    /**
     * Configure whether server should accept connections
     */
    public void setShouldAcceptConnection(boolean accept) {
        this.shouldAcceptConnection = accept;
    }

    /**
     * Set connection delay in milliseconds
     */
    public void setConnectionDelayMs(int delayMs) {
        this.connectionDelayMs = delayMs;
    }

    /**
     * Get messages received from clients
     */
    public List<String> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }

    /**
     * Wait for a specific message to be received
     */
    public String waitForMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return receivedMessages.poll(timeout, unit);
    }

    /**
     * Wait for N messages to be received
     */
    public List<String> waitForMessages(int count, long timeout, TimeUnit unit) throws InterruptedException {
        List<String> messages = new ArrayList<>();
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);

        while (messages.size() < count && System.currentTimeMillis() < deadline) {
            long remainingMs = deadline - System.currentTimeMillis();
            if (remainingMs <= 0) break;

            String message = receivedMessages.poll(remainingMs, TimeUnit.MILLISECONDS);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    /**
     * Clear received messages buffer
     */
    public void clearReceivedMessages() {
        receivedMessages.clear();
    }

    /**
     * Simulate server rejecting upgrade to WebSocket
     */
    public void rejectNextConnection() {
        server.enqueue(new MockResponse()
            .setResponseCode(403)
            .setBody("Forbidden"));
    }

    /**
     * Accept next WebSocket connection with custom behavior
     */
    public void acceptNextConnection() {
        server.enqueue(new MockResponse()
            .withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    activeWebSocket = webSocket;
                    log.info("WebSocket connection opened");

                    // Send any queued messages
                    for (String message : messagesToSend) {
                        webSocket.send(message);
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    log.info("Received message: {}", text);
                    receivedMessages.add(text);

                    // Echo back if in echo mode
                    if (echoMode) {
                        webSocket.send(text);
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    String text = bytes.utf8();
                    log.info("Received binary message (as text): {}", text);
                    receivedMessages.add(text);

                    if (echoMode) {
                        webSocket.send(text);
                    }
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    log.info("WebSocket closing: code={}, reason={}", code, reason);
                    webSocket.close(1000, null);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    log.info("WebSocket closed: code={}, reason={}", code, reason);
                    activeWebSocket = null;
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    log.error("WebSocket failure", t);
                    activeWebSocket = null;
                }
            }));
    }

    /**
     * Close active WebSocket connection from server side
     */
    public void closeConnection() {
        if (activeWebSocket != null) {
            activeWebSocket.close(1000, "Server closing connection");
            activeWebSocket = null;
        }
    }

    /**
     * Check if there's an active WebSocket connection
     */
    public boolean hasActiveConnection() {
        return activeWebSocket != null;
    }

    /**
     * Get count of received messages
     */
    public int getReceivedMessageCount() {
        return receivedMessages.size();
    }

    @Override
    public void close() throws IOException {
        if (activeWebSocket != null) {
            activeWebSocket.close(1000, "Server shutdown");
        }
        server.shutdown();
        log.info("Mock WebSocket server shut down");
    }

    /**
     * Builder for fluent configuration
     */
    public static class Builder {
        private boolean echoMode = false;
        private boolean shouldAccept = true;
        private int delayMs = 0;
        private final List<String> initialMessages = new ArrayList<>();

        public Builder echoMode() {
            this.echoMode = true;
            return this;
        }

        public Builder rejectConnections() {
            this.shouldAccept = false;
            return this;
        }

        public Builder connectionDelay(int ms) {
            this.delayMs = ms;
            return this;
        }

        public Builder sendOnConnect(String... messages) {
            for (String msg : messages) {
                initialMessages.add(msg);
            }
            return this;
        }

        public MockWebSocketServer build() throws IOException {
            MockWebSocketServer server = new MockWebSocketServer();
            server.setEchoMode(echoMode);
            server.setShouldAcceptConnection(shouldAccept);
            server.setConnectionDelayMs(delayMs);

            // Queue initial messages
            for (String message : initialMessages) {
                server.sendMessage(message);
            }

            // Accept first connection by default
            if (shouldAccept) {
                server.acceptNextConnection();
            }

            return server;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
