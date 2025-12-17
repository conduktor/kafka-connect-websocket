package io.conduktor.connect.websocket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.*;
import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JMX metrics registration and updates.
 * Addresses SME review finding: "HIGH: No tests verify JMX metrics registration and updates"
 */
class WebSocketMetricsTest {

    private WebSocketMetrics metrics;
    private MBeanServer mBeanServer;
    private String connectorName;
    private ObjectName objectName;

    @BeforeEach
    void setUp() throws Exception {
        connectorName = "test-connector-" + System.currentTimeMillis();
        mBeanServer = ManagementFactory.getPlatformMBeanServer();

        // Create metrics instance (registers MBean)
        metrics = new WebSocketMetrics(connectorName, "wss://test.example.com:9443/stream");

        // Construct expected ObjectName
        String sanitizedUrl = "test.example.com_9443_stream";
        objectName = new ObjectName(
            String.format("io.conduktor.connect.websocket:type=WebSocketConnector,name=\"%s\",url=\"%s\"",
                connectorName, sanitizedUrl)
        );
    }

    @AfterEach
    void tearDown() {
        if (metrics != null) {
            metrics.close();
        }
    }

    @Test
    void testMBeanRegistration() {
        // When: Metrics instance is created
        // Then: MBean should be registered
        assertTrue(mBeanServer.isRegistered(objectName),
            "MBean should be registered with JMX server");
    }

    @Test
    void testMBeanUnregistration() {
        // Given: Metrics instance exists and is registered
        assertTrue(mBeanServer.isRegistered(objectName));

        // When: Metrics is closed
        metrics.close();

        // Then: MBean should be unregistered
        assertFalse(mBeanServer.isRegistered(objectName),
            "MBean should be unregistered after close");
    }

    @Test
    void testInitialMetricValues() throws Exception {
        // When: Metrics is just created
        // Then: All counters should be zero or default values
        assertEquals(0L, getAttribute("MessagesReceived"));
        assertEquals(0L, getAttribute("MessagesDropped"));
        assertEquals(0L, getAttribute("RecordsProduced"));
        assertEquals(0, getAttribute("QueueSize"));
        assertEquals(0, getAttribute("QueueCapacity"));
        assertEquals(0.0, getAttribute("QueueUtilizationPercent"));
        assertFalse((Boolean) getAttribute("Connected"));
        assertEquals(-1L, getAttribute("MillisSinceLastMessage"));
        assertEquals(0L, getAttribute("LagCount"));
        assertEquals(0.0, getAttribute("DropRate"));
        assertEquals(0L, getAttribute("TotalReconnects"));
        assertEquals(connectorName, getAttribute("ConnectorName"));
    }

    @Test
    void testIncrementMessagesReceived() throws Exception {
        // Given: Initial count is 0
        assertEquals(0L, getAttribute("MessagesReceived"));

        // When: Incrementing messages received
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();

        // Then: Count should be updated
        assertEquals(3L, getAttribute("MessagesReceived"));

        // And: LastMessageTimestamp should be set
        long millisSince = (Long) getAttribute("MillisSinceLastMessage");
        assertTrue(millisSince >= 0 && millisSince < 1000,
            "Should show recent message within last second");
    }

    @Test
    void testIncrementMessagesDropped() throws Exception {
        // Given: Initial count is 0
        assertEquals(0L, getAttribute("MessagesDropped"));

        // When: Incrementing messages dropped
        metrics.incrementMessagesDropped();
        metrics.incrementMessagesDropped();

        // Then: Count should be updated
        assertEquals(2L, getAttribute("MessagesDropped"));
    }

    @Test
    void testIncrementRecordsProduced() throws Exception {
        // Given: Initial count is 0
        assertEquals(0L, getAttribute("RecordsProduced"));

        // When: Adding records produced (batch of 5, then 3)
        metrics.incrementRecordsProduced(5);
        metrics.incrementRecordsProduced(3);

        // Then: Count should be cumulative
        assertEquals(8L, getAttribute("RecordsProduced"));
    }

    @Test
    void testUpdateQueueSize() throws Exception {
        // Given: Initial queue size is 0
        assertEquals(0, getAttribute("QueueSize"));

        // When: Updating queue size
        metrics.updateQueueSize(42);

        // Then: Queue size should reflect new value
        assertEquals(42, getAttribute("QueueSize"));
    }

    @Test
    void testSetQueueCapacity() throws Exception {
        // Given: Initial capacity is 0
        assertEquals(0, getAttribute("QueueCapacity"));

        // When: Setting queue capacity
        metrics.setQueueCapacity(1000);

        // Then: Capacity should be updated
        assertEquals(1000, getAttribute("QueueCapacity"));
    }

    @Test
    void testQueueUtilizationPercent() throws Exception {
        // Given: Queue with capacity 100
        metrics.setQueueCapacity(100);
        assertEquals(0.0, getAttribute("QueueUtilizationPercent"));

        // When: Queue is 25% full
        metrics.updateQueueSize(25);

        // Then: Utilization should be 25%
        assertEquals(25.0, getAttribute("QueueUtilizationPercent"));

        // When: Queue is 80% full
        metrics.updateQueueSize(80);

        // Then: Utilization should be 80%
        assertEquals(80.0, getAttribute("QueueUtilizationPercent"));
    }

    @Test
    void testQueueUtilizationWithZeroCapacity() throws Exception {
        // Given: Queue with zero capacity (edge case)
        metrics.setQueueCapacity(0);
        metrics.updateQueueSize(10);

        // When: Getting utilization
        // Then: Should return 0 without throwing
        assertEquals(0.0, getAttribute("QueueUtilizationPercent"));
    }

    @Test
    void testSetConnected() throws Exception {
        // Given: Initially disconnected
        assertFalse((Boolean) getAttribute("Connected"));
        assertEquals(0L, getAttribute("UptimeMillis"));

        // When: Connection is established
        metrics.setConnected(true);

        // Then: Should show connected
        assertTrue((Boolean) getAttribute("Connected"));

        // And: Uptime should start tracking
        Thread.sleep(100);
        long uptime = (Long) getAttribute("UptimeMillis");
        assertTrue(uptime >= 100 && uptime < 500,
            "Uptime should be around 100ms, got: " + uptime);

        // When: Connection is lost
        metrics.setConnected(false);

        // Then: Should show disconnected
        assertFalse((Boolean) getAttribute("Connected"));
        assertEquals(0L, getAttribute("UptimeMillis"));
    }

    @Test
    void testIncrementReconnects() throws Exception {
        // Given: Initial reconnects is 0
        assertEquals(0L, getAttribute("TotalReconnects"));

        // When: Incrementing reconnects
        metrics.incrementReconnects();
        metrics.incrementReconnects();
        metrics.incrementReconnects();

        // Then: Count should be updated
        assertEquals(3L, getAttribute("TotalReconnects"));
    }

    @Test
    void testLagCount() throws Exception {
        // Given: Some messages received but not all produced
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived(); // 5 received

        metrics.incrementRecordsProduced(3); // 3 produced

        // When: Checking lag
        // Then: Lag should be 2 (5 - 3)
        assertEquals(2L, getAttribute("LagCount"));
    }

    @Test
    void testDropRate() throws Exception {
        // Given: 100 messages received, 10 dropped
        for (int i = 0; i < 100; i++) {
            metrics.incrementMessagesReceived();
        }
        for (int i = 0; i < 10; i++) {
            metrics.incrementMessagesDropped();
        }

        // When: Calculating drop rate
        // Then: Should be 10%
        assertEquals(10.0, getAttribute("DropRate"));
    }

    @Test
    void testDropRateWithZeroMessages() throws Exception {
        // Given: No messages received
        assertEquals(0L, getAttribute("MessagesReceived"));

        // When: Getting drop rate
        // Then: Should return 0 without throwing
        assertEquals(0.0, getAttribute("DropRate"));
    }

    @Test
    void testMillisSinceLastMessage() throws Exception {
        // Given: No messages yet
        assertEquals(-1L, getAttribute("MillisSinceLastMessage"));

        // When: Message is received
        metrics.incrementMessagesReceived();
        Thread.sleep(100);

        // Then: Should show time since last message
        long millisSince = (Long) getAttribute("MillisSinceLastMessage");
        assertTrue(millisSince >= 100 && millisSince < 500,
            "Should be around 100ms, got: " + millisSince);
    }

    @Test
    void testResetCounters() throws Exception {
        // Given: Metrics with some values
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesReceived();
        metrics.incrementMessagesDropped();
        metrics.incrementRecordsProduced(5);

        assertEquals(2L, getAttribute("MessagesReceived"));
        assertEquals(1L, getAttribute("MessagesDropped"));
        assertEquals(5L, getAttribute("RecordsProduced"));

        // When: Resetting counters via JMX operation
        mBeanServer.invoke(objectName, "resetCounters", new Object[]{}, new String[]{});

        // Then: All counters should be reset to 0
        assertEquals(0L, getAttribute("MessagesReceived"));
        assertEquals(0L, getAttribute("MessagesDropped"));
        assertEquals(0L, getAttribute("RecordsProduced"));
    }

    @Test
    void testGetConnectorName() throws Exception {
        // When: Getting connector name via JMX
        // Then: Should match the name provided at construction
        assertEquals(connectorName, getAttribute("ConnectorName"));
    }

    @Test
    void testConcurrentMetricUpdates() throws Exception {
        // Given: Multiple threads updating metrics
        int threadCount = 10;
        int incrementsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    metrics.incrementMessagesReceived();
                    metrics.incrementRecordsProduced(1);
                }
            });
        }

        // When: All threads update concurrently
        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        // Then: Final count should be accurate (no race conditions)
        long expectedCount = (long) threadCount * incrementsPerThread;
        assertEquals(expectedCount, getAttribute("MessagesReceived"));
        assertEquals(expectedCount, getAttribute("RecordsProduced"));
    }

    @Test
    void testMBeanReRegistrationAfterClose() throws Exception {
        // Given: Initial MBean is registered
        assertTrue(mBeanServer.isRegistered(objectName));

        // When: Closing and creating new instance with same name
        metrics.close();
        assertFalse(mBeanServer.isRegistered(objectName));

        metrics = new WebSocketMetrics(connectorName, "wss://test.example.com:9443/stream");

        // Then: New MBean should be registered without error
        assertTrue(mBeanServer.isRegistered(objectName));
    }

    @Test
    void testUrlSanitizationInObjectName() throws Exception {
        // Given: URL with special characters
        String urlWithSpecialChars = "wss://special-chars.example.com:443/path?query=value&other=123";
        String testConnectorName = "test-sanitization-" + System.currentTimeMillis();

        WebSocketMetrics testMetrics = new WebSocketMetrics(testConnectorName, urlWithSpecialChars);

        try {
            // When: Getting the registered MBean
            // Then: Should handle special characters properly
            assertNotNull(testMetrics);

            // Verify we can access the MBean (proves ObjectName is valid)
            ObjectName testObjectName = new ObjectName(
                "io.conduktor.connect.websocket:type=WebSocketConnector,name=\"" + testConnectorName + "\",url=*"
            );

            assertEquals(1, mBeanServer.queryMBeans(testObjectName, null).size(),
                "Should find exactly one MBean matching the pattern");
        } finally {
            testMetrics.close();
        }
    }

    /**
     * Helper method to get JMX attribute value
     */
    private Object getAttribute(String attributeName) throws Exception {
        return mBeanServer.getAttribute(objectName, attributeName);
    }
}
