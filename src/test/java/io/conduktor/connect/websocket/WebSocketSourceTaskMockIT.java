package io.conduktor.connect.websocket;

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using MockWebSocketServer instead of external dependencies.
 * Addresses SME review finding: "HIGH: Tests depend on external service (echo.websocket.org)"
 *
 * These tests provide:
 * - Deterministic behavior (no external service flakiness)
 * - Fast execution (local server)
 * - Full control over server responses
 * - Better assertion capabilities
 */
class WebSocketSourceTaskMockIT {

    private MockWebSocketServer mockServer;
    private WebSocketSourceTask task;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = MockWebSocketServer.builder()
            .echoMode()
            .build();
        task = new WebSocketSourceTask();
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
    void testSuccessfulConnection() throws Exception {
        // Given: Task configured to connect to mock server
        Map<String, String> props = createConfig(mockServer.getUrl());

        // When: Starting task
        task.start(props);

        // Wait for connection
        Thread.sleep(500);

        // Then: Server should have active connection
        assertTrue(mockServer.hasActiveConnection(),
            "Server should have accepted connection");
    }

    @Test
    void testReceiveMessageFromServer() throws Exception {
        // Given: Server configured to send messages
        mockServer.sendMessages("message1", "message2", "message3");

        Map<String, String> props = createConfig(mockServer.getUrl());
        task.start(props);

        // Wait for connection
        Thread.sleep(500);

        // When: Polling for records
        List<SourceRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                allRecords.addAll(records);
            }
            if (allRecords.size() >= 3) {
                break;
            }
            Thread.sleep(100);
        }

        // Then: Should receive all 3 messages
        assertTrue(allRecords.size() >= 3,
            String.format("Should receive at least 3 messages, got %d", allRecords.size()));

        // Verify message content
        Set<String> receivedMessages = new HashSet<>();
        for (SourceRecord record : allRecords) {
            receivedMessages.add(record.value().toString());
        }

        assertTrue(receivedMessages.contains("message1"), "Should receive message1");
        assertTrue(receivedMessages.contains("message2"), "Should receive message2");
        assertTrue(receivedMessages.contains("message3"), "Should receive message3");
    }

    @Test
    void testSubscriptionMessageSent() throws Exception {
        // Given: Task with subscription message
        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG,
                 "{\"action\":\"subscribe\",\"channel\":\"test-channel\"}");

        // When: Starting task (should send subscription)
        task.start(props);

        // Wait for subscription message
        String receivedMessage = mockServer.waitForMessage(2, TimeUnit.SECONDS);

        // Then: Server should receive subscription message
        assertNotNull(receivedMessage, "Server should receive subscription message");
        assertTrue(receivedMessage.contains("subscribe"),
            "Message should contain 'subscribe': " + receivedMessage);
        assertTrue(receivedMessage.contains("test-channel"),
            "Message should contain channel name: " + receivedMessage);
    }

    @Test
    void testEchoMode() throws Exception {
        // Given: Server in echo mode with subscription message
        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG,
                 "{\"action\":\"test\"}");

        // When: Starting task
        task.start(props);

        // Wait for echo
        Thread.sleep(1000);

        // Then: Should receive echo of subscription message
        List<SourceRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                allRecords.addAll(records);
                if (!allRecords.isEmpty()) break;
            }
            Thread.sleep(200);
        }

        assertFalse(allRecords.isEmpty(), "Should receive echoed message");

        SourceRecord record = allRecords.get(0);
        String message = record.value().toString();
        assertTrue(message.contains("action") && message.contains("test"),
            "Should receive echo of subscription: " + message);
    }

    @Test
    void testMultipleMessagesWithCorrectOffsets() throws Exception {
        // Given: Server sending multiple messages
        mockServer.sendMessages("msg1", "msg2", "msg3", "msg4", "msg5");

        Map<String, String> props = createConfig(mockServer.getUrl());
        task.start(props);

        Thread.sleep(500);

        // When: Polling for all messages
        List<SourceRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                allRecords.addAll(records);
            }
            if (allRecords.size() >= 5) {
                break;
            }
            Thread.sleep(100);
        }

        // Then: Should receive all messages with sequential offsets
        assertTrue(allRecords.size() >= 5,
            String.format("Should receive 5 messages, got %d", allRecords.size()));

        // Verify offsets are sequential
        Long lastSequence = null;
        for (SourceRecord record : allRecords) {
            Map<String, ?> offset = record.sourceOffset();
            assertNotNull(offset, "Offset should not be null");
            assertTrue(offset.containsKey("sequence"), "Offset should contain sequence");

            Long currentSequence = ((Number) offset.get("sequence")).longValue();
            if (lastSequence != null) {
                assertTrue(currentSequence > lastSequence,
                    String.format("Sequence should increase: %d -> %d", lastSequence, currentSequence));
            }
            lastSequence = currentSequence;
        }
    }

    @Test
    void testRecordMetadata() throws Exception {
        // Given: Server sending a message
        mockServer.sendMessage("test-message-content");

        Map<String, String> props = createConfig(mockServer.getUrl());
        String topicName = "test-kafka-topic";
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, topicName);
        task.start(props);

        Thread.sleep(500);

        // When: Polling for record
        List<SourceRecord> records = null;
        for (int i = 0; i < 10; i++) {
            records = task.poll();
            if (records != null && !records.isEmpty()) break;
            Thread.sleep(100);
        }

        // Then: Record should have correct metadata
        assertNotNull(records, "Should receive records");
        assertFalse(records.isEmpty(), "Should have at least one record");

        SourceRecord record = records.get(0);

        // Verify topic
        assertEquals(topicName, record.topic(), "Topic should match configuration");

        // Verify value schema and content
        assertEquals(org.apache.kafka.connect.data.Schema.STRING_SCHEMA, record.valueSchema());
        assertEquals("test-message-content", record.value());

        // Verify source partition
        Map<String, ?> partition = record.sourcePartition();
        assertNotNull(partition);
        assertTrue(partition.containsKey("websocket_url"));
        assertEquals(mockServer.getUrl(), partition.get("websocket_url"));

        // Verify source offset structure
        Map<String, ?> offset = record.sourceOffset();
        assertNotNull(offset);
        assertTrue(offset.containsKey("session_id"), "Should have session_id");
        assertTrue(offset.containsKey("sequence"), "Should have sequence");
        assertInstanceOf(String.class, offset.get("session_id"));
        assertInstanceOf(Number.class, offset.get("sequence"));

        // Verify timestamp is recent
        assertNotNull(record.timestamp(), "Timestamp should be set");
        long now = System.currentTimeMillis();
        long recordTime = record.timestamp();
        assertTrue(Math.abs(now - recordTime) < 5000,
            String.format("Timestamp should be recent (within 5s): now=%d, record=%d", now, recordTime));
    }

    @Test
    void testConnectionFailureHandling() throws Exception {
        // Given: Task configured with invalid URL
        Map<String, String> props = createConfig("ws://localhost:9999"); // No server here
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // When: Starting task
        // Then: Should not throw (connection happens asynchronously)
        assertDoesNotThrow(() -> task.start(props));

        // Polling should return null (no connection)
        Thread.sleep(1000);
        List<SourceRecord> records = task.poll();
        assertTrue(records == null || records.isEmpty(),
            "Should not receive records when connection fails");

        // Should be able to stop cleanly
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    void testReconnectionDisabled() throws Exception {
        // Given: Server that will close connection
        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        task.start(props);
        Thread.sleep(500);

        // When: Server closes connection
        mockServer.closeConnection();
        Thread.sleep(500);

        // Then: Task should not reconnect (no active connection)
        assertFalse(mockServer.hasActiveConnection(),
            "Task should not reconnect when reconnect is disabled");
    }

    @Test
    void testCustomHeaders() throws Exception {
        // Given: Task with custom headers
        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG,
                 "User-Agent:TestClient,X-Custom-Header:custom-value");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test");

        // When: Starting task
        task.start(props);

        // Then: Should connect successfully with custom headers
        // (Headers are validated but we can't inspect them on mock server side)
        Thread.sleep(500);
        assertTrue(mockServer.hasActiveConnection(),
            "Should connect with custom headers");

        // Verify subscription was sent
        String receivedMessage = mockServer.waitForMessage(1, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "Should receive subscription message");
    }

    @Test
    void testAuthTokenHeader() throws Exception {
        // Given: Task with auth token
        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "test-token-12345");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test");

        // When: Starting task
        task.start(props);

        // Then: Should connect with Authorization header
        Thread.sleep(500);
        assertTrue(mockServer.hasActiveConnection(),
            "Should connect with auth token");

        // Verify subscription was sent
        String receivedMessage = mockServer.waitForMessage(1, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "Should receive subscription message");
    }

    @Test
    void testHighVolumeMessages() throws Exception {
        // Given: Server sending many messages
        int messageCount = 100;
        for (int i = 0; i < messageCount; i++) {
            mockServer.sendMessage("message-" + i);
        }

        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "200");
        task.start(props);

        Thread.sleep(1000);

        // When: Polling for all messages
        List<SourceRecord> allRecords = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < deadline && allRecords.size() < messageCount) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                allRecords.addAll(records);
            }
            Thread.sleep(50);
        }

        // Then: Should receive most or all messages
        assertTrue(allRecords.size() >= messageCount * 0.9,
            String.format("Should receive at least 90%% of messages (90/%d), got %d",
                        messageCount, allRecords.size()));
    }

    @Test
    void testQueueOverflow() throws Exception {
        // Given: Small queue and many messages
        int messageCount = 50;
        for (int i = 0; i < messageCount; i++) {
            mockServer.sendMessage("message-" + i);
        }

        Map<String, String> props = createConfig(mockServer.getUrl());
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "10"); // Small queue
        task.start(props);

        // Don't poll immediately - let queue overflow
        Thread.sleep(1000);

        // When: Finally polling
        List<SourceRecord> allRecords = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                allRecords.addAll(records);
            }
            Thread.sleep(50);
        }

        // Then: Should receive some messages, but likely not all (queue overflow)
        assertTrue(allRecords.size() > 0, "Should receive some messages");
        assertTrue(allRecords.size() < messageCount,
            "Should lose some messages due to queue overflow");
    }

    /**
     * Helper to create test configuration
     */
    private Map<String, String> createConfig(String websocketUrl) {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, websocketUrl);
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");
        return props;
    }
}
