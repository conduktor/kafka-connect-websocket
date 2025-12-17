package io.conduktor.connect.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMX Metrics for WebSocket Source Connector.
 * Exposes operational metrics via JMX MBeans for monitoring.
 */
public class WebSocketMetrics implements WebSocketMetricsMBean, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebSocketMetrics.class);

    private final String connectorName;
    private final ObjectName objectName;
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesDropped = new AtomicLong(0);
    private final AtomicLong recordsProduced = new AtomicLong(0);
    private volatile int currentQueueSize = 0;
    private volatile int queueCapacity = 0;
    private volatile boolean isConnected = false;
    private volatile long lastMessageTimestamp = 0;
    private volatile long connectionStartTime = 0;
    private volatile long totalReconnects = 0;

    public WebSocketMetrics(String connectorName, String websocketUrl) throws JMException {
        this.connectorName = connectorName;

        // Create JMX ObjectName
        String sanitizedUrl = sanitizeUrl(websocketUrl);
        this.objectName = new ObjectName(
            String.format("io.conduktor.connect.websocket:type=WebSocketConnector,name=%s,url=%s",
                ObjectName.quote(connectorName),
                ObjectName.quote(sanitizedUrl))
        );

        // Register MBean
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            if (mbs.isRegistered(objectName)) {
                log.warn("MBean already registered, unregistering old instance: {}", objectName);
                mbs.unregisterMBean(objectName);
            }
            mbs.registerMBean(this, objectName);
            log.info("Registered JMX MBean: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to register JMX MBean: {}", objectName, e);
            throw new JMException("Failed to register MBean: " + e.getMessage());
        }
    }

    private String sanitizeUrl(String url) {
        if (url == null) return "unknown";
        // Remove protocol and special characters for JMX name
        return url.replaceAll("^(ws|wss)://", "")
                  .replaceAll("[^a-zA-Z0-9._-]", "_")
                  .substring(0, Math.min(50, url.length())); // Limit length
    }

    // Metric update methods

    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
        lastMessageTimestamp = System.currentTimeMillis();
    }

    public void incrementMessagesDropped() {
        messagesDropped.incrementAndGet();
    }

    public void incrementRecordsProduced(long count) {
        recordsProduced.addAndGet(count);
    }

    public void updateQueueSize(int size) {
        this.currentQueueSize = size;
    }

    public void setQueueCapacity(int capacity) {
        this.queueCapacity = capacity;
    }

    public void setConnected(boolean connected) {
        if (connected && !this.isConnected) {
            // Connection established
            connectionStartTime = System.currentTimeMillis();
        }
        this.isConnected = connected;
    }

    public void incrementReconnects() {
        totalReconnects++;
    }

    // JMX MBean interface implementation

    @Override
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    @Override
    public long getMessagesDropped() {
        return messagesDropped.get();
    }

    @Override
    public long getRecordsProduced() {
        return recordsProduced.get();
    }

    @Override
    public int getQueueSize() {
        return currentQueueSize;
    }

    @Override
    public int getQueueCapacity() {
        return queueCapacity;
    }

    @Override
    public double getQueueUtilizationPercent() {
        if (queueCapacity == 0) return 0.0;
        return (currentQueueSize * 100.0) / queueCapacity;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public long getMillisSinceLastMessage() {
        if (lastMessageTimestamp == 0) return -1;
        return System.currentTimeMillis() - lastMessageTimestamp;
    }

    @Override
    public long getLagCount() {
        return messagesReceived.get() - recordsProduced.get();
    }

    @Override
    public long getUptimeMillis() {
        if (connectionStartTime == 0 || !isConnected) return 0;
        return System.currentTimeMillis() - connectionStartTime;
    }

    @Override
    public long getTotalReconnects() {
        return totalReconnects;
    }

    @Override
    public double getDropRate() {
        long received = messagesReceived.get();
        if (received == 0) return 0.0;
        return (messagesDropped.get() * 100.0) / received;
    }

    @Override
    public void resetCounters() {
        messagesReceived.set(0);
        messagesDropped.set(0);
        recordsProduced.set(0);
        log.info("Reset metrics counters for connector: {}", connectorName);
    }

    @Override
    public String getConnectorName() {
        return connectorName;
    }

    @Override
    public void close() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            if (mbs.isRegistered(objectName)) {
                mbs.unregisterMBean(objectName);
                log.info("Unregistered JMX MBean: {}", objectName);
            }
        } catch (Exception e) {
            log.error("Failed to unregister JMX MBean: {}", objectName, e);
        }
    }
}

/**
 * MBean interface for WebSocket metrics.
 * All getter methods are exposed as JMX attributes.
 */
interface WebSocketMetricsMBean {

    // Counter metrics
    long getMessagesReceived();
    long getMessagesDropped();
    long getRecordsProduced();

    // Queue metrics
    int getQueueSize();
    int getQueueCapacity();
    double getQueueUtilizationPercent();

    // Connection metrics
    boolean isConnected();
    long getMillisSinceLastMessage();
    long getUptimeMillis();
    long getTotalReconnects();

    // Derived metrics
    long getLagCount();
    double getDropRate();

    // Metadata
    String getConnectorName();

    // Operations
    void resetCounters();
}
