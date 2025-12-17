package io.conduktor.connect.websocket;

/**
 * MBean interface for WebSocket metrics.
 * All getter methods are exposed as JMX attributes.
 */
public interface WebSocketMetricsMBean {

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
