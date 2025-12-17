package io.conduktor.connect.websocket;

import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.conduktor.connect.websocket.TestWaiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test using MockWebSocketServer.
 * Addresses SME review finding: "HIGH: Tests depend on external service (echo.websocket.org)"
 */
class WebSocketSourceTaskIT {

    private WebSocketSourceTask task;
    private MockWebSocketServer mockServer;
    private SourceTaskContext mockContext;

    @BeforeEach
    void setUp() throws Exception {
        task = new WebSocketSourceTask();
        mockServer = MockWebSocketServer.builder().echoMode().build();

        // Initialize task context (required by Kafka Connect contract)
        mockContext = mock(SourceTaskContext.class);
        OffsetStorageReader mockOffsetReader = mock(OffsetStorageReader.class);
        when(mockContext.offsetStorageReader()).thenReturn(mockOffsetReader);
        task.initialize(mockContext);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (task != null) {
            task.stop();
        }
        if (mockServer != null) {
            mockServer.close();
        }
    }

    @Test
    void testEchoServerConnection() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");

        task.start(props);

        // Wait for connection to establish (deterministic waiting)
        waitUntil(mockServer::hasActiveConnection, "Connection should be established");

        // Verify the task started successfully
        assertNotNull(task.version());

        // Poll a few times (should return null or empty since no messages sent yet)
        for (int i = 0; i < 5; i++) {
            List<SourceRecord> records = task.poll();
            assertTrue(records == null || records.isEmpty(), "No messages expected yet");
        }

        // Task should still be running without errors
        task.stop();
    }

    @Test
    void testTaskStartWithInvalidUrl() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://invalid-host-9999.example.com:9999");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing (connection happens asynchronously)
        assertDoesNotThrow(() -> task.start(props));

        // Give it time to attempt connection (but it will fail)
        waitFor(1000);

        // Polling should return null (no connection established)
        List<SourceRecord> records = task.poll();
        assertTrue(records == null || records.isEmpty(),
            "Should not receive records when URL cannot be reached");

        // Stop should also work fine
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    void testTaskWithSubscriptionMessage() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"subscribe\",\"channel\":\"test\"}");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        task.start(props);

        // Wait for connection and subscription message to be echoed (deterministic)
        String echoed = waitForMessage(mockServer, 5, TimeUnit.SECONDS);
        assertTrue(echoed.contains("subscribe"), "Server should receive subscription message");

        // Poll - the mock server should echo back our subscription message (deterministic)
        List<SourceRecord> records = waitForNonNull((TestWaiter.ThrowingSupplier<List<SourceRecord>>) () -> {
            List<SourceRecord> polled = task.poll();
            return (polled != null && !polled.isEmpty()) ? polled : null;
        }, "Should receive echoed message");

        // We should have received the echo of our subscription message
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


}
