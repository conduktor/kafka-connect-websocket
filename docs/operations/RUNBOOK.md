# WebSocket Source Connector - Operational Runbook

This runbook provides operational guidance for monitoring, troubleshooting, and maintaining the WebSocket Source Connector in production.

## Table of Contents

- [Monitoring](#monitoring)
- [Common Issues](#common-issues)
- [Performance Tuning](#performance-tuning)
- [Troubleshooting](#troubleshooting)
- [Recovery Procedures](#recovery-procedures)

## Monitoring

### Key JMX Metrics

The connector exposes the following JMX metrics under `io.conduktor.connect.websocket:type=WebSocketConnector,name=<connector-name>,url=<sanitized-url>`:

#### Counter Metrics
- **MessagesReceived**: Total messages received from WebSocket
  - **Alert**: Rate drops to 0 for > 5 minutes → Connection issue
  - **Action**: Check `isConnected` metric, review logs for connection errors

- **MessagesDropped**: Messages dropped due to queue overflow
  - **Alert**: Drop rate > 1% → Queue capacity insufficient
  - **Action**: Increase `websocket.message.queue.size` or optimize consumer throughput

- **RecordsProduced**: Total records written to Kafka
  - **Alert**: Lag (MessagesReceived - RecordsProduced) > 10000 → Processing backlog
  - **Action**: Check Kafka broker health, review consumer lag

#### Queue Metrics
- **QueueSize**: Current number of messages in queue
- **QueueCapacity**: Maximum queue capacity
- **QueueUtilizationPercent**: (QueueSize / QueueCapacity) * 100
  - **Alert**: Utilization > 80% → Approaching capacity
  - **Action**: Monitor for drops, consider increasing queue size

#### Connection Metrics
- **isConnected**: Boolean indicating WebSocket connection status
  - **Alert**: false for > 2 minutes → Connection failure
  - **Action**: Check WebSocket endpoint availability, review authentication

- **MillisSinceLastMessage**: Time since last message received
  - **Alert**: > 300000ms (5 minutes) when messages expected → Stale connection
  - **Action**: Check endpoint health, verify reconnection logic

- **UptimeMillis**: Connection uptime in milliseconds
  - **Info**: Use to track connection stability

- **TotalReconnects**: Total reconnection attempts
  - **Alert**: Rapid increase (>10 in 5 minutes) → Unstable connection
  - **Action**: Review connection stability, check network/firewall

#### Derived Metrics
- **LagCount**: MessagesReceived - RecordsProduced
  - **Alert**: > 10000 → Processing backlog
  - **Action**: Review Kafka consumer performance

- **DropRate**: (MessagesDropped / MessagesReceived) * 100
  - **Alert**: > 1% → Significant message loss
  - **Action**: Increase queue size or optimize throughput

### Recommended Alerts

```yaml
# Example Prometheus alerting rules
groups:
  - name: websocket_connector
    rules:
      - alert: WebSocketDisconnected
        expr: websocket_isConnected == 0
        for: 2m
        annotations:
          summary: "WebSocket connection lost for {{ $labels.connector_name }}"
          description: "Connection to {{ $labels.url }} has been down for 2 minutes"

      - alert: HighMessageDropRate
        expr: websocket_DropRate > 1
        for: 5m
        annotations:
          summary: "High message drop rate for {{ $labels.connector_name }}"
          description: "Dropping {{ $value }}% of messages - queue capacity insufficient"

      - alert: HighProcessingLag
        expr: websocket_LagCount > 10000
        for: 5m
        annotations:
          summary: "High processing lag for {{ $labels.connector_name }}"
          description: "Lag count: {{ $value }} - Kafka throughput issue"

      - alert: FrequentReconnections
        expr: increase(websocket_TotalReconnects[5m]) > 10
        annotations:
          summary: "Frequent reconnections for {{ $labels.connector_name }}"
          description: "{{ $value }} reconnections in 5 minutes - unstable connection"
```

## Common Issues

### Issue 1: Connection Keeps Dropping

**Symptoms:**
- `isConnected` metric flipping between true/false
- `TotalReconnects` incrementing rapidly
- Logs showing "Connection closed" or "Connection failed"

**Root Causes:**
1. Network instability
2. WebSocket endpoint issues
3. Firewall/proxy timeout
4. Authentication token expiration

**Resolution:**
```bash
# Check connector status
curl -s http://localhost:8083/connectors/websocket-source/status | jq .

# Review recent logs
kubectl logs -l app=kafka-connect --tail=100 | grep "WebSocket"

# Verify endpoint health
curl -H "Connection: Upgrade" -H "Upgrade: websocket" <websocket-url>

# Actions:
1. Increase websocket.connection.timeout.ms (default: 30000)
2. Enable reconnection: websocket.reconnect.enabled=true
3. Adjust reconnect interval: websocket.reconnect.interval.ms=10000
4. Check authentication token validity
```

### Issue 2: High Message Drop Rate

**Symptoms:**
- `DropRate` metric > 1%
- `MessagesDropped` incrementing
- `QueueUtilizationPercent` at 100%
- Logs showing "Queue full, dropping message"

**Root Causes:**
1. Queue capacity too small
2. Kafka broker throughput bottleneck
3. Consumer lag

**Resolution:**
```bash
# Check queue metrics
jconsole # Connect to JMX and view queue metrics

# Actions:
1. Increase queue size: websocket.message.queue.size=20000 (default: 10000)
2. Check Kafka broker health:
   kafka-broker-api-versions --bootstrap-server localhost:9092
3. Monitor consumer lag:
   kafka-consumer-groups --bootstrap-server localhost:9092 --group <group> --describe
4. Scale Kafka brokers if needed
```

### Issue 3: No Messages Received

**Symptoms:**
- `MessagesReceived` not incrementing
- `isConnected` = true
- No errors in logs

**Root Causes:**
1. Subscription message not sent or incorrect
2. WebSocket endpoint not sending data
3. Endpoint requires specific subscription format

**Resolution:**
```bash
# Verify subscription message format
cat connector-config.json | jq .config.\"websocket.subscription.message\"

# Test endpoint manually
wscat -c wss://api.example.com/stream -x '{"action":"subscribe","channel":"test"}'

# Actions:
1. Verify subscription message syntax matches endpoint requirements
2. Check endpoint documentation for subscription format
3. Test with curl/wscat to confirm endpoint is sending data
4. Review MillisSinceLastMessage metric
```

### Issue 4: Processing Lag Building Up

**Symptoms:**
- `LagCount` increasing
- `QueueSize` growing
- `RecordsProduced` not keeping pace with `MessagesReceived`

**Root Causes:**
1. Kafka broker slowness
2. Network issues to Kafka
3. Kafka topic partitions insufficient
4. Serialization bottleneck

**Resolution:**
```bash
# Check Kafka broker metrics
kafka-run-class kafka.tools.JmxTool \
  --object-name kafka.server:type=BrokerTopicMetrics,name=MessagesInPerSec \
  --jmx-url service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi

# Check topic configuration
kafka-topics --bootstrap-server localhost:9092 --describe --topic <topic-name>

# Actions:
1. Increase topic partitions for parallelism
2. Check Kafka broker disk I/O and network throughput
3. Monitor Kafka producer metrics
4. Review connector logs for serialization errors
```

## Performance Tuning

### Queue Sizing

**Default:** 10000 messages

**Recommendations:**
- Low-volume, low-latency: 1000-5000
- Medium-volume: 10000-20000 (default)
- High-volume (>1000 msg/sec): 50000-100000

**Trade-offs:**
- Larger queue = more memory usage
- Smaller queue = more drops under burst traffic

### Reconnection Settings

**Production Recommendations:**
```properties
websocket.reconnect.enabled=true
websocket.reconnect.interval.ms=5000
websocket.reconnect.max.attempts=-1  # infinite retries
websocket.reconnect.backoff.max.ms=60000
```

**Dev/Test Recommendations:**
```properties
websocket.reconnect.enabled=true
websocket.reconnect.interval.ms=1000
websocket.reconnect.max.attempts=10
websocket.reconnect.backoff.max.ms=10000
```

### Connection Timeout

**Default:** 30000ms (30 seconds)

**Adjust based on network latency:**
- Local network: 10000ms
- Same region: 30000ms (default)
- Cross-region: 60000ms
- Unstable network: 90000ms

## Troubleshooting

### Debug Logging

Enable debug logging for detailed troubleshooting:

```properties
# Connector logging
log4j.logger.io.conduktor.connect.websocket=DEBUG

# OkHttp WebSocket logging
log4j.logger.okhttp3=DEBUG
```

### Common Log Messages

| Log Message | Severity | Meaning | Action |
|-------------|----------|---------|--------|
| "Connection opened" | INFO | WebSocket connected | Normal operation |
| "Connection closed: code=1000" | INFO | Normal closure | Reconnection will occur if enabled |
| "Connection failed" | ERROR | Connection attempt failed | Check endpoint availability |
| "Queue full, dropping message" | WARN | Queue overflow | Increase queue size |
| "Failed to send subscription message" | ERROR | Subscription failed | Verify message format |
| "Reconnecting in X ms" | INFO | Waiting to reconnect | Normal if occasional |

### Health Check

Create a health check script:

```bash
#!/bin/bash
# health-check.sh

CONNECTOR_NAME="websocket-source"
CONNECT_URL="http://localhost:8083"

# Check connector status
STATUS=$(curl -s $CONNECT_URL/connectors/$CONNECTOR_NAME/status | jq -r '.connector.state')

if [ "$STATUS" != "RUNNING" ]; then
  echo "CRITICAL: Connector not running (state: $STATUS)"
  exit 2
fi

# Check task status
TASK_STATUS=$(curl -s $CONNECT_URL/connectors/$CONNECTOR_NAME/status | jq -r '.tasks[0].state')

if [ "$TASK_STATUS" != "RUNNING" ]; then
  echo "CRITICAL: Task not running (state: $TASK_STATUS)"
  exit 2
fi

# Check JMX metrics (requires jmxterm or similar)
# Check if connected
CONNECTED=$(echo "get -b io.conduktor.connect.websocket:type=WebSocketConnector,name=$CONNECTOR_NAME,url=* isConnected" | java -jar jmxterm.jar -l localhost:9999 -n)

if [ "$CONNECTED" != "true" ]; then
  echo "WARNING: WebSocket not connected"
  exit 1
fi

echo "OK: Connector healthy"
exit 0
```

## Recovery Procedures

### Restart Connector

```bash
# Pause connector
curl -X PUT http://localhost:8083/connectors/websocket-source/pause

# Wait for current messages to flush
sleep 10

# Resume connector
curl -X PUT http://localhost:8083/connectors/websocket-source/resume

# Verify status
curl -s http://localhost:8083/connectors/websocket-source/status | jq .
```

### Reset Offsets

**WARNING:** This will cause message reprocessing or data loss depending on retention.

```bash
# Delete connector
curl -X DELETE http://localhost:8083/connectors/websocket-source

# Connector offsets are stored in Kafka Connect's offset storage
# Manual reset requires stopping Connect, clearing offset topic, and restarting

# Recreate connector with fresh configuration
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connector-config.json
```

### Emergency Shutdown

If connector is misbehaving and needs immediate shutdown:

```bash
# Option 1: Pause connector (graceful)
curl -X PUT http://localhost:8083/connectors/websocket-source/pause

# Option 2: Delete connector (removes from cluster)
curl -X DELETE http://localhost:8083/connectors/websocket-source

# Option 3: Restart Connect worker (nuclear option)
kubectl delete pod -l app=kafka-connect
```

## Contact and Escalation

For issues not covered in this runbook:

1. Review connector logs at DEBUG level
2. Check Kafka Connect worker logs
3. Verify Kafka broker health
4. Consult project documentation: https://github.com/conduktor/kafka-connect-websocket
5. Open issue with:
   - Connector configuration
   - JMX metrics snapshot
   - Relevant logs (last 1000 lines)
   - Kafka Connect worker version
   - Kafka broker version
