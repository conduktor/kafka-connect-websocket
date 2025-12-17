package io.conduktor.connect.websocket;

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test using the WebSocket.org echo server.
 */
class WebSocketSourceTaskIT {

    private WebSocketSourceTask task;

    @BeforeEach
    void setUp() {
        task = new WebSocketSourceTask();
    }

    @AfterEach
    void tearDown() {
        if (task != null) {
            task.stop();
        }
    }

    @Test
    void testEchoServerConnection() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");

        task.start(props);

        // Wait for connection to establish
        Thread.sleep(2000);

        // The echo server doesn't send messages automatically, so we can't easily test message receipt
        // But we can verify the task started successfully
        assertNotNull(task.version());

        // Poll a few times (should return null or empty since no messages)
        for (int i = 0; i < 5; i++) {
            List<SourceRecord> records = task.poll();
            // No messages expected from echo server without sending first
            assertTrue(records == null || records.isEmpty());
        }

        // Task should still be running without errors
        task.stop();
    }

    @Test
    void testTaskStartWithInvalidUrl() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://invalid.url.that.does.not.exist.12345");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing (connection happens asynchronously)
        assertDoesNotThrow(() -> task.start(props));

        // Wait for connection attempt
        Thread.sleep(1000);

        // Polling should return null (no connection established)
        List<SourceRecord> records = task.poll();
        assertTrue(records == null || records.isEmpty(),
            "Should not receive records when URL is invalid");

        // Stop should also work fine
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    void testTaskWithSubscriptionMessage() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"subscribe\",\"channel\":\"test\"}");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");

        task.start(props);

        // Wait for connection and subscription
        Thread.sleep(2000);

        // Poll - the echo server should echo back our subscription message
        List<SourceRecord> records = null;
        for (int i = 0; i < 10 && (records == null || records.isEmpty()); i++) {
            records = task.poll();
            if (records != null && !records.isEmpty()) {
                break;
            }
            Thread.sleep(500);
        }

        // We should have received the echo of our subscription message
        assertNotNull(records, "Should have received echoed message");
        assertFalse(records.isEmpty(), "Should have at least one record");

        SourceRecord record = records.get(0);
        assertEquals("test-topic", record.topic(), "Topic should match configuration");
        assertNotNull(record.value(), "Record value should not be null");
        assertTrue(record.value().toString().contains("subscribe"),
            "Echo should contain 'subscribe': " + record.value());

        // Verify offset structure
        Map<String, ?> offset = record.sourceOffset();
        assertNotNull(offset, "Offset should not be null");
        assertTrue(offset.containsKey("session_id"), "Offset should contain session_id");
        assertTrue(offset.containsKey("sequence"), "Offset should contain sequence");

        // Verify partition structure
        Map<String, ?> partition = record.sourcePartition();
        assertNotNull(partition, "Partition should not be null");
        assertTrue(partition.containsKey("websocket_url"), "Partition should contain websocket_url");

        task.stop();
    }

    @Test
    void testHeaderParsing() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "User-Agent:TestClient,X-Custom-Header:value123");

        // Should start successfully with custom headers
        assertDoesNotThrow(() -> task.start(props));
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    void testAuthToken() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "test-token-12345");

        // Should start successfully with auth token
        assertDoesNotThrow(() -> task.start(props));
        assertDoesNotThrow(() -> task.stop());
    }
}
