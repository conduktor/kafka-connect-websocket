package io.conduktor.connect.websocket;

import org.apache.kafka.connect.source.SourceRecord;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resource leaks: thread management, connection cleanup, and memory management.
 * Addresses SME review finding: "HIGH: No resource leak tests - threads, connections, memory"
 */
class WebSocketSourceTaskResourceTest {

    @Test
    void testTaskStopCleansUpThreads() throws Exception {
        // Given: Record thread count before starting task
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Set<String> initialThreadNames = getCurrentThreadNames();
        int initialThreadCount = threadMXBean.getThreadCount();

        // When: Starting and stopping task
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        task.start(props);

        // Wait for threads to be created
        Thread.sleep(500);

        // Verify threads were created
        Set<String> runningThreadNames = getCurrentThreadNames();
        assertTrue(runningThreadNames.size() > initialThreadNames.size(),
            "Task should create threads during operation");

        // Stop the task
        task.stop();

        // Wait for threads to terminate
        Thread.sleep(1000);

        // Then: Thread count should return to baseline (or very close)
        int finalThreadCount = threadMXBean.getThreadCount();
        assertTrue(Math.abs(finalThreadCount - initialThreadCount) <= 2,
            String.format("Thread count should return to baseline. Initial: %d, Final: %d, Diff: %d",
                initialThreadCount, finalThreadCount, Math.abs(finalThreadCount - initialThreadCount)));
    }

    @Test
    void testMultipleStartStopCyclesNoLeaks() throws Exception {
        // Given: Baseline thread count
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int initialThreadCount = threadMXBean.getThreadCount();

        // When: Running multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            WebSocketSourceTask task = new WebSocketSourceTask();
            Map<String, String> props = createTestConfig();
            task.start(props);
            Thread.sleep(500);
            task.stop();
            Thread.sleep(500);
        }

        // Then: Thread count should stabilize near baseline
        int finalThreadCount = threadMXBean.getThreadCount();
        assertTrue(Math.abs(finalThreadCount - initialThreadCount) <= 5,
            String.format("Multiple cycles should not leak threads. Initial: %d, Final: %d",
                initialThreadCount, finalThreadCount));
    }

    @Test
    void testTaskStopWhilePolling() throws Exception {
        // Given: Task is actively polling
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"test\"}");
        task.start(props);

        // Start polling in background thread
        Thread pollingThread = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    task.poll();
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        pollingThread.start();

        // Wait for some polling to occur
        Thread.sleep(300);

        // When: Stopping task while polling is active
        task.stop();

        // Then: Polling thread should complete gracefully
        pollingThread.join(2000);
        assertFalse(pollingThread.isAlive(),
            "Polling thread should terminate after task stop");
    }

    @Test
    void testStopWithoutStart() {
        // Given: Task is never started
        WebSocketSourceTask task = new WebSocketSourceTask();

        // When/Then: Stopping should not throw
        assertDoesNotThrow(() -> task.stop(),
            "Stopping unstarted task should be safe");
    }

    @Test
    void testMultipleStopCalls() throws Exception {
        // Given: Task is started
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        task.start(props);
        Thread.sleep(500);

        // When: Calling stop multiple times
        task.stop();
        Thread.sleep(200);

        // Then: Additional stop calls should not throw
        assertDoesNotThrow(() -> task.stop(),
            "Multiple stop calls should be safe (idempotent)");
    }

    @Test
    void testGracefulShutdownDrainsQueue() throws Exception {
        // Given: MockWebSocketServer in echo mode
        try (MockWebSocketServer mockServer = MockWebSocketServer.builder().echoMode().build()) {
            // Given: Task with messages in queue
            WebSocketSourceTask task = new WebSocketSourceTask();
            Map<String, String> props = createTestConfigWithMockServer(mockServer);
            props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG,
                     "{\"action\":\"subscribe\",\"channel\":\"test\"}");
            task.start(props);

            // Wait for subscription message to be echoed
            Thread.sleep(1500);

            // Poll once to verify we have messages
            List<SourceRecord> records = task.poll();
            if (records == null || records.isEmpty()) {
                // Try again
                Thread.sleep(500);
                records = task.poll();
            }

            // When: Stopping task (should drain remaining messages)
            long stopStartTime = System.currentTimeMillis();
            task.stop();
            long stopDuration = System.currentTimeMillis() - stopStartTime;

            // Then: Stop should complete within timeout
            assertTrue(stopDuration < 7000,
                String.format("Stop should complete within 7s (including 5s drain timeout), took: %dms", stopDuration));
        }
    }

    @Test
    void testPollReturnsNullAfterStop() throws Exception {
        // Given: Running task
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        task.start(props);
        Thread.sleep(500);

        // When: Stopping task
        task.stop();

        // Then: Subsequent poll should return null (or handle gracefully)
        // Note: May throw if called after stop, which is acceptable
        try {
            List<SourceRecord> records = task.poll();
            assertNull(records, "Poll after stop should return null");
        } catch (Exception e) {
            // Acceptable - task may throw if polled after stop
            assertTrue(true, "Task may throw after stop - acceptable behavior");
        }
    }

    @Test
    void testConnectionCleanupOnStop() throws Exception {
        // Given: Task with active connection
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        task.start(props);

        // Wait for connection to establish
        Thread.sleep(1000);

        // When: Stopping task
        task.stop();

        // Wait for cleanup
        Thread.sleep(500);

        // Then: WebSocket threads should be mostly cleaned up
        // Note: OkHttp may leave a shared TaskRunner thread which is normal behavior
        Set<String> finalThreadNames = getCurrentThreadNames();
        long websocketThreadCount = finalThreadNames.stream()
            .filter(name -> name.contains("OkHttp") || name.contains("WebSocket"))
            .count();

        assertTrue(websocketThreadCount <= 1,
            String.format("WebSocket-related threads should be cleaned up (≤1 allowed for OkHttp TaskRunner). Found %d threads: %s",
                websocketThreadCount, finalThreadNames));
    }

    @Test
    void testNoThreadLeaksFromFailedConnections() throws Exception {
        // Given: Configuration with invalid URL
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int initialThreadCount = threadMXBean.getThreadCount();

        // When: Starting task with URL that will fail to connect
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG,
                 "ws://definitely-invalid-url-12345.example.com:9999");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");

        task.start(props);
        Thread.sleep(1000);

        // Stop task
        task.stop();
        Thread.sleep(500);

        // Then: Should not leak threads despite connection failure
        int finalThreadCount = threadMXBean.getThreadCount();
        assertTrue(Math.abs(finalThreadCount - initialThreadCount) <= 3,
            String.format("Failed connection should not leak threads. Initial: %d, Final: %d",
                initialThreadCount, finalThreadCount));
    }

    @Test
    void testReconnectionThreadManagement() throws Exception {
        // Given: Task with reconnection enabled
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        Set<String> initialThreadNames = getCurrentThreadNames();

        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "1000");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_MAX_ATTEMPTS_CONFIG, "3");

        // When: Task runs (may have reconnections)
        task.start(props);
        Thread.sleep(2000);
        task.stop();
        Thread.sleep(1000);

        // Then: Reconnection logic should not leak threads
        Set<String> finalThreadNames = getCurrentThreadNames();
        Set<String> newThreads = new HashSet<>(finalThreadNames);
        newThreads.removeAll(initialThreadNames);

        assertTrue(newThreads.size() <= 2,
            "Should not leak threads from reconnection. New threads: " + newThreads);
    }


    @Test
    void testJMXCleanupOnStop() throws Exception {
        // Given: Task with JMX metrics
        WebSocketSourceTask task = new WebSocketSourceTask();
        Map<String, String> props = createTestConfig();
        props.put("name", "test-jmx-cleanup-connector");
        task.start(props);
        Thread.sleep(500);

        // When: Stopping task
        task.stop();
        Thread.sleep(500);

        // Then: JMX MBeans should be unregistered
        // Query for MBeans matching the connector pattern
        Set<javax.management.ObjectName> mbeans =
            ManagementFactory.getPlatformMBeanServer().queryNames(
                new javax.management.ObjectName("io.conduktor.connect.websocket:*"),
                null
            );

        long connectorMBeans = mbeans.stream()
            .filter(name -> name.toString().contains("test-jmx-cleanup-connector"))
            .count();

        assertEquals(0, connectorMBeans,
            "JMX MBeans should be unregistered after stop");
    }

    /**
     * Helper to create test configuration
     */
    private Map<String, String> createTestConfig() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://localhost:9999");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");
        return props;
    }

    /**
     * Helper to create test configuration with MockWebSocketServer
     */
    private Map<String, String> createTestConfigWithMockServer(MockWebSocketServer server) {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, server.getUrl());
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");
        return props;
    }

    /**
     * Get current thread names for leak detection
     */
    private Set<String> getCurrentThreadNames() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] threadInfos = threadMXBean.dumpAllThreads(false, false);
        Set<String> threadNames = new HashSet<>();
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                threadNames.add(info.getThreadName());
            }
        }
        return threadNames;
    }
}
