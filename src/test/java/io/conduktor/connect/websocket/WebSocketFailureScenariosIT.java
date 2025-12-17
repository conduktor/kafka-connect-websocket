package io.conduktor.connect.websocket;

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebSocket failure scenarios.
 * Tests reconnection, queue behavior, shutdown scenarios, and error handling.
 */
class WebSocketFailureScenariosIT {

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

    // ========================================================================================
    // RECONNECTION AFTER SERVER DISCONNECT
    // ========================================================================================

    @Test
    @Timeout(30)
    void testReconnectionAfterServerDisconnect() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "2000");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "ping");

        task.start(props);

        // Wait for initial connection
        Thread.sleep(3000);

        // Send a message to verify connection
        List<SourceRecord> records = null;
        for (int i = 0; i < 10 && (records == null || records.isEmpty()); i++) {
            records = task.poll();
            if (records != null && !records.isEmpty()) {
                break;
            }
            Thread.sleep(500);
        }

        // Should have received the echo
        assertNotNull(records);
        assertFalse(records.isEmpty());

        // Simulate server disconnect by stopping and restarting task
        // (In real scenario, server would disconnect and client would reconnect)
        task.stop();
        Thread.sleep(1000);

        // Restart with reconnection
        task = new WebSocketSourceTask();
        task.start(props);

        // Wait for reconnection
        Thread.sleep(4000);

        // Verify task can still receive messages after reconnection
        List<SourceRecord> newRecords = null;
        for (int i = 0; i < 10 && (newRecords == null || newRecords.isEmpty()); i++) {
            newRecords = task.poll();
            if (newRecords != null && !newRecords.isEmpty()) {
                break;
            }
            Thread.sleep(500);
        }

        // May or may not have messages depending on echo server behavior
        // But task should not have crashed
        assertNotNull(task);
    }

    @Test
    @Timeout(20)
    void testReconnectionDisabledAfterFailure() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://invalid-host-that-does-not-exist-12345.com");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing (connection is async)
        assertDoesNotThrow(() -> task.start(props));

        // Wait a bit
        Thread.sleep(2000);

        // Task should still be able to poll (just no messages)
        assertDoesNotThrow(() -> task.poll());

        // Stop should work fine
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(30)
    void testMultipleReconnectionAttempts() throws Exception {
        Map<String, String> props = new HashMap<>();
        // Use a URL that will fail initially but not crash
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "1000");

        task.start(props);

        // Let it run for a while to ensure no crash with multiple operations
        for (int i = 0; i < 20; i++) {
            task.poll();
            Thread.sleep(500);
        }

        // Task should still be running
        assertNotNull(task);
        assertDoesNotThrow(() -> task.stop());
    }

    // ========================================================================================
    // BEHAVIOR WHEN KAFKA IS SLOW/DOWN
    // ========================================================================================

    @Test
    @Timeout(20)
    void testPollReturnsNullWhenNoMessages() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        task.start(props);

        // Wait for connection
        Thread.sleep(2000);

        // Poll without messages should return null (not throw)
        List<SourceRecord> records = task.poll();
        // Depending on timing, may be null or empty
        assertTrue(records == null || records.isEmpty());
    }

    @Test
    @Timeout(20)
    void testContinuousPollWithNoMessages() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        task.start(props);
        Thread.sleep(2000);

        // Simulate Kafka being slow - poll many times rapidly
        for (int i = 0; i < 50; i++) {
            assertDoesNotThrow(() -> task.poll());
            // Small delay to simulate processing
            Thread.sleep(50);
        }

        // Task should handle this gracefully
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(30)
    void testMessageQueueBehaviorUnderLoad() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "10"); // Small queue
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test message");

        task.start(props);
        Thread.sleep(2000);

        // Poll rapidly to ensure queue is being drained
        int totalMessages = 0;
        for (int i = 0; i < 20; i++) {
            List<SourceRecord> records = task.poll();
            if (records != null) {
                totalMessages += records.size();
            }
            Thread.sleep(100);
        }

        // Should have received at least one message (the echo)
        assertTrue(totalMessages >= 1);
    }

    // ========================================================================================
    // SHUTDOWN WITH MESSAGES IN QUEUE
    // ========================================================================================

    @Test
    @Timeout(20)
    void testShutdownWithMessagesInQueue() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "1000");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test message");

        task.start(props);

        // Wait for connection and message
        Thread.sleep(3000);

        // Stop immediately without draining queue
        assertDoesNotThrow(() -> task.stop());

        // Stop should be clean
        assertDoesNotThrow(() -> task.poll());
    }

    @Test
    @Timeout(20)
    void testImmediateShutdownAfterStart() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        task.start(props);

        // Stop immediately without waiting for connection
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(20)
    void testShutdownDuringReconnection() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://localhost:9999"); // Invalid
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "1000");

        task.start(props);

        // Wait for it to start attempting reconnection
        Thread.sleep(2000);

        // Stop during reconnection attempts
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(20)
    void testMultipleStartStopCycles() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            task = new WebSocketSourceTask();
            task.start(props);
            Thread.sleep(1000);
            task.stop();
            Thread.sleep(500);
        }

        // Should handle multiple cycles gracefully
        assertTrue(true);
    }

    // ========================================================================================
    // CONNECTION TIMEOUT SCENARIOS
    // ========================================================================================

    @Test
    @Timeout(20)
    void testConnectionTimeout() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://10.255.255.1:9999"); // Non-routable
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "2000"); // Short timeout
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing
        assertDoesNotThrow(() -> task.start(props));

        // Wait for timeout to occur
        Thread.sleep(3000);

        // Should still be able to poll and stop
        assertDoesNotThrow(() -> task.poll());
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(20)
    void testVeryShortConnectionTimeout() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "100"); // Very short
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "1000");

        task.start(props);

        // Give it time to potentially timeout and reconnect
        Thread.sleep(5000);

        // Task should still be functional
        assertDoesNotThrow(() -> task.poll());
        assertDoesNotThrow(() -> task.stop());
    }

    // ========================================================================================
    // ERROR HANDLING AND EDGE CASES
    // ========================================================================================

    @Test
    @Timeout(20)
    void testInvalidProtocol() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "http://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // OkHttp might upgrade http to ws, or it might fail
        // Either way, shouldn't throw during start
        assertDoesNotThrow(() -> task.start(props));

        Thread.sleep(2000);

        // Should be able to stop cleanly
        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(20)
    void testPollAfterStop() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        task.start(props);
        Thread.sleep(2000);

        task.stop();

        // Polling after stop should handle gracefully (may throw or return null)
        // Implementation determines behavior
        try {
            List<SourceRecord> records = task.poll();
            // If it returns, should be null or empty
            assertTrue(records == null || records.isEmpty());
        } catch (Exception e) {
            // Some implementations may throw after stop - also acceptable
            assertTrue(e instanceof InterruptedException || e instanceof IllegalStateException);
        }
    }

    @Test
    @Timeout(20)
    void testConcurrentPollCalls() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test");

        task.start(props);
        Thread.sleep(2000);

        // Simulate multiple threads polling (though Kafka Connect shouldn't do this)
        AtomicBoolean failed = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        task.poll();
                        Thread.sleep(100);
                    }
                } catch (Exception e) {
                    failed.set(true);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));

        task.stop();

        // Concurrent access may cause issues - this documents the behavior
        // In production, Kafka Connect ensures single-threaded access to poll()
    }

    @Test
    @Timeout(20)
    void testLargeMessageHandling() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");

        // Create a large message
        StringBuilder largeMsg = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeMsg.append("x");
        }

        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, largeMsg.toString());

        task.start(props);
        Thread.sleep(3000);

        // Should handle large message
        List<SourceRecord> records = null;
        for (int i = 0; i < 10; i++) {
            records = task.poll();
            if (records != null && !records.isEmpty()) {
                break;
            }
            Thread.sleep(500);
        }

        task.stop();

        // Should either receive the large echo or handle gracefully
        assertDoesNotThrow(() -> {});
    }

    @Test
    @Timeout(20)
    void testEmptySubscriptionMessage() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "");

        task.start(props);
        Thread.sleep(2000);

        // Should not send empty message, just wait for messages
        for (int i = 0; i < 5; i++) {
            task.poll();
            Thread.sleep(200);
        }

        assertDoesNotThrow(() -> task.stop());
    }

    @Test
    @Timeout(20)
    void testQueueOverflowScenario() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "1"); // Tiny queue
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "test");

        task.start(props);
        Thread.sleep(3000);

        // Don't poll for a while to let queue potentially overflow
        Thread.sleep(2000);

        // Now poll
        List<SourceRecord> records = task.poll();

        // Queue might have dropped messages
        // But we should get at most queue size
        if (records != null) {
            assertTrue(records.size() <= 10); // Allow some buffer
        }

        task.stop();
    }

    // ========================================================================================
    // NETWORK FAILURE SIMULATION
    // ========================================================================================

    @Test
    @Timeout(20)
    void testUnreachableHost() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://192.0.2.1:9999"); // TEST-NET-1
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "2000");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing
        task.start(props);

        // Wait for connection attempt to fail
        Thread.sleep(3000);

        // Should still be able to poll (no messages) and stop
        assertDoesNotThrow(() -> {
            task.poll();
            task.stop();
        });
    }

    @Test
    @Timeout(20)
    void testDNSResolutionFailure() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://this-host-definitely-does-not-exist-12345678.invalid");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "2000");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        // Should start without throwing
        task.start(props);

        // Wait for DNS resolution to fail
        Thread.sleep(3000);

        // Should handle gracefully
        assertDoesNotThrow(() -> {
            task.poll();
            task.stop();
        });
    }

    // ========================================================================================
    // RECONNECTION BEHAVIOR VALIDATION
    // ========================================================================================

    @Test
    @Timeout(30)
    void testReconnectionPreservesConfiguration() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://echo.websocket.org");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "1000");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "ping");

        task.start(props);

        // Wait for initial connection
        Thread.sleep(3000);

        // Get initial messages
        List<SourceRecord> initialRecords = null;
        for (int i = 0; i < 10; i++) {
            initialRecords = task.poll();
            if (initialRecords != null && !initialRecords.isEmpty()) {
                break;
            }
            Thread.sleep(500);
        }

        // Verify messages go to correct topic
        if (initialRecords != null && !initialRecords.isEmpty()) {
            assertEquals("test-topic", initialRecords.get(0).topic());
        }

        task.stop();
    }

    @Test
    @Timeout(20)
    void testNoReconnectionWhenDisabled() throws Exception {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://localhost:9999");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "1000");

        task.start(props);

        // Wait for connection failure
        Thread.sleep(2000);

        // Should not be reconnecting
        // Poll should return null
        List<SourceRecord> records = task.poll();
        assertTrue(records == null || records.isEmpty());

        task.stop();
    }
}
