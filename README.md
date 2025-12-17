# Kafka Connect WebSocket Source Connector

A Kafka Connect source connector for streaming data from WebSocket endpoints into Apache Kafka topics.

## Features

- Stream real-time data from any WebSocket endpoint (ws:// or wss://)
- Support for subscription messages (for exchanges like Binance, Coinbase, etc.)
- Automatic reconnection with configurable intervals
- Custom header support for authentication
- Bearer token authentication support
- Configurable message buffering and queue management
- Built on OkHttp WebSocket client for reliability
- Comprehensive logging and metrics

## Quick Start

### Prerequisites

- Java 11 or higher
- Apache Kafka 3.9.0 or higher
- Maven 3.6+ (for building)

### Building

```bash
mvn clean package
```

This will create `kafka-connect-websocket-1.0.0.jar` in the `target/` directory.

### Installation

The connector can be deployed in two ways: as an uber JAR with bundled dependencies, or as a plugin directory structure. Choose the method that best fits your Kafka Connect setup.

#### Option 1: Uber JAR with Dependencies (Recommended)

Build an uber JAR that includes all required dependencies (OkHttp and its transitive dependencies):

```bash
# Build the uber JAR with dependencies
mvn clean package shade:shade

# Verify the JAR includes dependencies
jar tf target/kafka-connect-websocket-1.0.0.jar | grep okhttp
```

Create the plugin directory and copy the JAR:

```bash
# Create plugin directory
mkdir -p $KAFKA_HOME/plugins/kafka-connect-websocket

# Copy the uber JAR
cp target/kafka-connect-websocket-1.0.0.jar $KAFKA_HOME/plugins/kafka-connect-websocket/
```

#### Option 2: Plugin Directory with Separate Dependencies

If your build doesn't create an uber JAR, manually package all dependencies:

```bash
# Create plugin directory
mkdir -p $KAFKA_HOME/plugins/kafka-connect-websocket

# Copy the connector JAR
cp target/kafka-connect-websocket-1.0.0.jar $KAFKA_HOME/plugins/kafka-connect-websocket/

# Copy all runtime dependencies (OkHttp and transitive deps)
mvn dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=$KAFKA_HOME/plugins/kafka-connect-websocket/
```

This should include:
- `okhttp-4.12.0.jar`
- `okio-3.x.x.jar` (OkHttp dependency)
- `kotlin-stdlib-x.x.x.jar` (OkHttp dependency)

#### Configure Plugin Path

Ensure your Kafka Connect configuration includes the plugins directory:

**For Distributed Mode** (`config/connect-distributed.properties`):
```properties
plugin.path=/usr/local/share/kafka/plugins,$KAFKA_HOME/plugins
```

**For Standalone Mode** (`config/connect-standalone.properties`):
```properties
plugin.path=/usr/local/share/kafka/plugins,$KAFKA_HOME/plugins
```

#### Verify Installation

Restart Kafka Connect and verify the connector is loaded:

```bash
# Restart Kafka Connect (distributed mode example)
systemctl restart kafka-connect

# Check available connectors
curl -s http://localhost:8083/connector-plugins | jq '.[] | select(.class | contains("WebSocket"))'
```

Expected output:
```json
{
  "class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
  "type": "source",
  "version": "1.0.0"
}
```

If the connector doesn't appear:
1. Check `connect.log` for ClassNotFoundException or NoClassDefFoundError
2. Verify all dependency JARs are present in the plugin directory
3. Confirm `plugin.path` is correctly configured
4. Ensure the plugin directory has proper read permissions

## Configuration

### Required Parameters

| Parameter | Description |
|-----------|-------------|
| `websocket.url` | WebSocket endpoint URL (ws:// or wss://) |
| `kafka.topic` | Target Kafka topic for messages |

### Optional Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `websocket.subscription.message` | null | JSON message to send after connection (for subscribing) |
| `websocket.reconnect.enabled` | true | Enable automatic reconnection |
| `websocket.reconnect.interval.ms` | 5000 | Interval between reconnection attempts |
| `websocket.headers` | null | Custom headers (format: key1:value1,key2:value2) |
| `websocket.auth.token` | null | Bearer token for Authorization header |
| `websocket.message.queue.size` | 10000 | Maximum message buffer size |
| `websocket.connection.timeout.ms` | 30000 | Connection timeout |

## Examples

### 1. WebSocket Echo Server (Testing)

```json
{
  "name": "websocket-echo-connector",
  "config": {
    "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
    "tasks.max": "1",
    "websocket.url": "wss://echo.websocket.org",
    "kafka.topic": "websocket-echo",
    "websocket.subscription.message": "{\"action\":\"subscribe\",\"channel\":\"test\"}",
    "websocket.reconnect.enabled": "true",
    "websocket.reconnect.interval.ms": "5000"
  }
}
```

### 2. Binance WebSocket (Cryptocurrency Prices)

```json
{
  "name": "binance-btcusdt-connector",
  "config": {
    "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
    "tasks.max": "1",
    "websocket.url": "wss://stream.binance.com:9443/ws",
    "kafka.topic": "binance-btcusdt-trades",
    "websocket.subscription.message": "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}",
    "websocket.reconnect.enabled": "true",
    "websocket.reconnect.interval.ms": "3000"
  }
}
```

### 3. Coinbase WebSocket (Level 2 Order Book)

```json
{
  "name": "coinbase-btc-usd-connector",
  "config": {
    "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
    "tasks.max": "1",
    "websocket.url": "wss://ws-feed.exchange.coinbase.com",
    "kafka.topic": "coinbase-btc-usd-level2",
    "websocket.subscription.message": "{\"type\":\"subscribe\",\"product_ids\":[\"BTC-USD\"],\"channels\":[\"level2\"]}",
    "websocket.reconnect.enabled": "true"
  }
}
```

### 4. With Custom Headers and Authentication

```json
{
  "name": "authenticated-websocket-connector",
  "config": {
    "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
    "tasks.max": "1",
    "websocket.url": "wss://api.example.com/ws",
    "kafka.topic": "authenticated-stream",
    "websocket.auth.token": "your-bearer-token-here",
    "websocket.headers": "User-Agent:KafkaConnectWebSocket/1.0,X-Custom-Header:value",
    "websocket.reconnect.enabled": "true"
  }
}
```

## Deploying the Connector

### Using Kafka Connect REST API

Create a connector:
```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @connector-config.json
```

Check connector status:
```bash
curl http://localhost:8083/connectors/websocket-echo-connector/status
```

Delete a connector:
```bash
curl -X DELETE http://localhost:8083/connectors/websocket-echo-connector
```

### Using Kafka Connect CLI

Start in standalone mode:
```bash
$KAFKA_HOME/bin/connect-standalone.sh \
  config/connect-standalone.properties \
  config/websocket-source-connector.properties
```

## Monitoring

### Built-in Metrics and Logging

The connector logs metrics every 30 seconds including:
- Connection status
- Messages received
- Records produced to Kafka
- Reconnection attempts
- Queue utilization (current/max)

Check the logs:
```bash
tail -f $KAFKA_HOME/logs/connect.log
```

Example log output:
```
[2025-12-17 10:30:45,123] INFO [WebSocketSourceTask] Metrics - Connection: OPEN, Received: 15234, Produced: 15234, Queue: 42/10000 (0.42%), Reconnects: 0
```

### JMX Metrics

Kafka Connect exposes JMX metrics that can be monitored using tools like Prometheus, Grafana, or JConsole.

#### Enable JMX

Configure JMX in Kafka Connect startup script:

```bash
# For distributed mode
export KAFKA_JMX_OPTS="-Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9999 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \
  -Djava.rmi.server.hostname=localhost"

$KAFKA_HOME/bin/connect-distributed.sh config/connect-distributed.properties
```

#### Available Metrics

**Connector-Level Metrics** (MBean: `kafka.connect:type=connector-metrics,connector=websocket-connector`):

| Metric | Type | Description |
|--------|------|-------------|
| `status` | String | Connector status (RUNNING, FAILED, PAUSED) |
| `connector-total-task-count` | Gauge | Number of tasks (always 1 for WebSocket) |
| `connector-failed-task-count` | Gauge | Number of failed tasks |

**Task-Level Metrics** (MBean: `kafka.connect:type=source-task-metrics,connector=websocket-connector,task=0`):

| Metric | Type | Description |
|--------|------|-------------|
| `source-record-poll-total` | Counter | Total records polled from WebSocket |
| `source-record-poll-rate` | Gauge | Records polled per second |
| `source-record-write-total` | Counter | Total records written to Kafka |
| `source-record-write-rate` | Gauge | Records written per second |
| `source-record-active-count` | Gauge | Records currently being processed |
| `poll-batch-avg-time-ms` | Gauge | Average time per poll batch |

**Worker-Level Metrics** (MBean: `kafka.connect:type=connect-worker-metrics`):

| Metric | Type | Description |
|--------|------|-------------|
| `task-count` | Gauge | Total tasks across all connectors |
| `connector-count` | Gauge | Total connectors |
| `connector-startup-attempts-total` | Counter | Connector startup attempts |
| `connector-startup-failure-total` | Counter | Failed connector startups |

#### Query JMX Metrics

Using `jmxterm`:
```bash
# Install jmxterm
wget https://github.com/jiaqi/jmxterm/releases/download/v1.0.2/jmxterm-1.0.2-uber.jar

# Connect and query
java -jar jmxterm-1.0.2-uber.jar -l localhost:9999
> domain kafka.connect
> beans
> get -b kafka.connect:type=source-task-metrics,connector=websocket-connector,task=0 source-record-poll-rate
```

Using `jconsole` (GUI):
```bash
jconsole localhost:9999
# Navigate to MBeans tab → kafka.connect → source-task-metrics
```

### Prometheus Integration

Export Kafka Connect JMX metrics to Prometheus using JMX Exporter.

#### Setup JMX Exporter

1. **Download JMX Exporter:**
```bash
wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.20.0/jmx_prometheus_javaagent-0.20.0.jar \
  -O /opt/jmx_prometheus_javaagent.jar
```

2. **Create configuration file** (`/opt/jmx-exporter-config.yml`):
```yaml
lowercaseOutputName: true
lowercaseOutputLabelNames: true
whitelistObjectNames:
  - "kafka.connect:type=connector-metrics,connector=*"
  - "kafka.connect:type=source-task-metrics,connector=*,task=*"
  - "kafka.connect:type=connect-worker-metrics"
  - "kafka.producer:type=producer-metrics,client-id=*"
rules:
  - pattern: 'kafka.connect<type=(.+), connector=(.+)><>(.+):'
    name: kafka_connect_$1_$3
    labels:
      connector: "$2"
  - pattern: 'kafka.connect<type=(.+), connector=(.+), task=(.+)><>(.+):'
    name: kafka_connect_$1_$4
    labels:
      connector: "$2"
      task: "$3"
```

3. **Configure Kafka Connect to use JMX Exporter:**
```bash
export KAFKA_OPTS="-javaagent:/opt/jmx_prometheus_javaagent.jar=8080:/opt/jmx-exporter-config.yml"
$KAFKA_HOME/bin/connect-distributed.sh config/connect-distributed.properties
```

4. **Verify metrics endpoint:**
```bash
curl http://localhost:8080/metrics | grep kafka_connect
```

#### Prometheus Scrape Configuration

Add to `prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'kafka-connect'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'
    scrape_interval: 15s
```

### Grafana Dashboard

Import or create a Grafana dashboard with these panels:

#### Key Panels

1. **Connection Status**
   - Query: `up{job="kafka-connect"}`
   - Visualization: Stat panel (Green=1, Red=0)

2. **Message Throughput**
   - Query: `rate(kafka_connect_source_task_metrics_source_record_poll_total[5m])`
   - Visualization: Graph (messages/second)

3. **Queue Utilization** (requires custom metric logging)
   - Parse from logs using Loki or similar
   - Visualization: Gauge (0-100%)
   - Alert threshold: >70%

4. **Kafka Write Rate**
   - Query: `rate(kafka_connect_source_task_metrics_source_record_write_total[5m])`
   - Visualization: Graph (records/second)

5. **Reconnection Events** (requires custom metric)
   - Parse from logs: `grep "Reconnection attempt" connect.log`
   - Visualization: Counter

6. **Processing Latency**
   - Query: `kafka_connect_source_task_metrics_poll_batch_avg_time_ms`
   - Visualization: Graph (milliseconds)

### Alert Configuration

#### Prometheus Alerting Rules

Create `/etc/prometheus/rules/kafka-connect.yml`:
```yaml
groups:
  - name: kafka_connect_websocket
    interval: 30s
    rules:
      - alert: ConnectorDown
        expr: up{job="kafka-connect"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Kafka Connect is down"
          description: "Kafka Connect has been down for more than 2 minutes"

      - alert: LowThroughput
        expr: rate(kafka_connect_source_task_metrics_source_record_poll_total[5m]) < 1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Low message throughput"
          description: "WebSocket connector receiving < 1 message/sec for 5 minutes"

      - alert: NoMessagesReceived
        expr: rate(kafka_connect_source_task_metrics_source_record_poll_total[10m]) == 0
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "No messages received from WebSocket"
          description: "No messages received for 10 minutes - possible connection issue"

      - alert: WriteRateLaggingBehindPollRate
        expr: |
          rate(kafka_connect_source_task_metrics_source_record_poll_total[5m]) -
          rate(kafka_connect_source_task_metrics_source_record_write_total[5m]) > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka write rate lagging behind poll rate"
          description: "Messages may be accumulating in queue - possible Kafka throughput issue"

      - alert: HighProcessingLatency
        expr: kafka_connect_source_task_metrics_poll_batch_avg_time_ms > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High message processing latency"
          description: "Average processing time > 1 second for 5 minutes"
```

### Log Aggregation

#### ELK Stack (Elasticsearch, Logstash, Kibana)

**Logstash configuration** (`/etc/logstash/conf.d/kafka-connect.conf`):
```ruby
input {
  file {
    path => "/var/log/kafka/connect.log"
    start_position => "beginning"
    codec => multiline {
      pattern => "^\[%{TIMESTAMP_ISO8601}"
      negate => true
      what => "previous"
    }
  }
}

filter {
  grok {
    match => {
      "message" => "\[%{TIMESTAMP_ISO8601:timestamp}\] %{LOGLEVEL:level} \[%{DATA:task}\] %{GREEDYDATA:message_text}"
    }
  }

  # Extract queue utilization
  if [message_text] =~ /Queue utilization/ {
    grok {
      match => {
        "message_text" => "Queue utilization: %{NUMBER:queue_current:int}/%{NUMBER:queue_max:int} \(%{NUMBER:queue_percent:float}%\)"
      }
    }
  }

  # Extract reconnection attempts
  if [message_text] =~ /Reconnection attempt/ {
    grok {
      match => {
        "message_text" => "Reconnection attempt %{NUMBER:reconnect_attempt:int}"
      }
    }
  }

  date {
    match => ["timestamp", "ISO8601"]
    target => "@timestamp"
  }
}

output {
  elasticsearch {
    hosts => ["localhost:9200"]
    index => "kafka-connect-%{+YYYY.MM.dd}"
  }
}
```

**Useful Kibana queries:**
```
# Queue utilization over time
message_text:"Queue utilization" AND queue_percent:>70

# All reconnection events
message_text:"Reconnection attempt"

# Connection failures
level:ERROR AND message_text:"Failed to connect"

# SSL/TLS errors
message_text:"SSLHandshakeException"
```

#### Loki/Promtail (Lightweight Alternative)

**Promtail configuration** (`/etc/promtail/config.yml`):
```yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  - job_name: kafka-connect
    static_configs:
      - targets:
          - localhost
        labels:
          job: kafka-connect
          __path__: /var/log/kafka/connect.log
    pipeline_stages:
      - regex:
          expression: '\[(?P<timestamp>.*?)\] (?P<level>\w+) \[(?P<task>.*?)\] (?P<message>.*)'
      - labels:
          level:
          task:
      - timestamp:
          source: timestamp
          format: '2006-01-02 15:04:05,000'
```

**LogQL queries in Grafana:**
```logql
# Queue utilization warnings
{job="kafka-connect"} |= "Queue utilization" | logfmt | queue_percent > 70

# Reconnection events rate
rate({job="kafka-connect"} |= "Reconnection attempt" [5m])

# Error log stream
{job="kafka-connect"} |= "ERROR"
```

### Recommended Monitoring Checklist

Set up the following for production deployments:

- [ ] JMX enabled with secure authentication
- [ ] Prometheus scraping Kafka Connect metrics
- [ ] Grafana dashboard for visualization
- [ ] Alerts for:
  - [ ] Connector down
  - [ ] No messages received (>5 minutes)
  - [ ] Queue utilization >70%
  - [ ] High reconnection rate (>5 per hour)
  - [ ] Write rate lagging poll rate
- [ ] Log aggregation (ELK or Loki)
- [ ] Automated log parsing for queue metrics
- [ ] Daily health check reports
- [ ] On-call rotation for critical alerts

### Health Check Endpoint

Create a simple health check script:

```bash
#!/bin/bash
# health-check.sh - Monitor WebSocket connector health

CONNECT_URL="http://localhost:8083"
CONNECTOR_NAME="websocket-connector"

# Check connector status
STATUS=$(curl -s "$CONNECT_URL/connectors/$CONNECTOR_NAME/status" | jq -r '.connector.state')

if [ "$STATUS" != "RUNNING" ]; then
    echo "CRITICAL: Connector state is $STATUS"
    exit 2
fi

# Check task status
TASK_STATUS=$(curl -s "$CONNECT_URL/connectors/$CONNECTOR_NAME/status" | jq -r '.tasks[0].state')

if [ "$TASK_STATUS" != "RUNNING" ]; then
    echo "CRITICAL: Task state is $TASK_STATUS"
    exit 2
fi

# Check message throughput (requires log parsing)
RECENT_MESSAGES=$(tail -100 /var/log/kafka/connect.log | grep -c "Received:")

if [ "$RECENT_MESSAGES" -eq 0 ]; then
    echo "WARNING: No recent messages in last 100 log lines"
    exit 1
fi

echo "OK: Connector and task running, messages flowing"
exit 0
```

Run periodically via cron:
```cron
*/5 * * * * /opt/kafka/health-check.sh || mail -s "Kafka Connect Alert" ops@example.com
```

## Architecture

### Components

1. **WebSocketSourceConnector**: Main connector class that manages configuration and task creation
2. **WebSocketSourceTask**: Task that handles WebSocket connection and message polling
3. **WebSocketClient**: OkHttp-based WebSocket client with reconnection logic
4. **WebSocketSourceConnectorConfig**: Configuration definition and validation

### Data Flow

```
WebSocket Endpoint
        ↓
  WebSocketClient (OkHttp)
        ↓
  Message Queue (LinkedBlockingDeque)
        ↓
  WebSocketSourceTask.poll()
        ↓
  SourceRecord (String schema)
        ↓
    Kafka Topic
```

## Limitations

- Single task per connector (WebSocket connections are single-threaded)
- Messages are consumed as strings (no automatic deserialization)
- No offset management (WebSocket protocol limitation)
- Cannot resume from a specific point after restart
- Messages in the queue are lost on connector shutdown

## Data Loss Warning and Mitigation

### Understanding At-Most-Once Semantics

**CRITICAL**: This connector implements **at-most-once** delivery semantics, meaning messages can be lost but will never be duplicated. This is fundamentally different from Kafka's typical at-least-once or exactly-once guarantees.

### When Data Loss Occurs

Data loss happens in the following scenarios:

1. **Connector Shutdown/Restart**
   - All messages in the in-memory queue (up to `websocket.message.queue.size`) are permanently lost
   - No recovery is possible after restart
   - Impact: Last N seconds of data (depends on throughput and queue size)

2. **Queue Overflow**
   - When messages arrive faster than Kafka can accept them
   - Oldest messages are silently dropped from the queue
   - Watch for `Queue full, dropping oldest message` in logs

3. **WebSocket Connection Loss**
   - Messages sent during disconnection are never received
   - Reconnection does not replay historical data (WebSocket protocol limitation)
   - Gap in data timeline between disconnect and reconnect

4. **Network Issues**
   - Messages lost during network partitions
   - No acknowledgment or retry mechanism at WebSocket protocol level

5. **Kafka Connect Worker Crash**
   - All buffered messages lost
   - No state persistence across crashes

### Why This Happens

The WebSocket protocol is fundamentally different from Kafka:
- **No offsets**: WebSocket streams have no concept of message position
- **No replay**: Cannot request historical messages from most WebSocket servers
- **Ephemeral**: Data is pushed once and never stored server-side
- **Stateless**: Server doesn't track what clients have received

### Mitigation Strategies

#### 1. Reduce In-Memory Buffer Risk

Minimize queue size to reduce potential loss window:

```properties
# Smaller queue = less data at risk (but higher risk of overflow)
websocket.message.queue.size=1000
```

Trade-off: Smaller queues overflow more easily on traffic bursts.

#### 2. Monitor Queue Utilization

Set up alerts when queue utilization exceeds 70%:

```bash
# Monitor connector logs for queue metrics
tail -f connect.log | grep "Queue utilization"
```

Example log message:
```
[WebSocketSourceTask] Queue utilization: 7234/10000 (72.34%)
```

#### 3. Increase Kafka Producer Throughput

Optimize Kafka producer settings in Connect worker configuration:

```properties
# In connect-distributed.properties or connect-standalone.properties
producer.linger.ms=10
producer.batch.size=32768
producer.compression.type=lz4
producer.buffer.memory=67108864
```

#### 4. Deploy Redundant Connectors

For critical data streams, run multiple connector instances:

```json
{
  "name": "binance-btcusdt-primary",
  "config": {
    "websocket.url": "wss://stream.binance.com:9443/ws",
    "kafka.topic": "binance-btcusdt-trades"
  }
}
```

```json
{
  "name": "binance-btcusdt-secondary",
  "config": {
    "websocket.url": "wss://stream.binance.com:9443/ws",
    "kafka.topic": "binance-btcusdt-trades"
  }
}
```

Deduplicate in downstream consumers using message timestamps and unique IDs.

#### 5. Use High-Availability Kafka Connect Cluster

Deploy Kafka Connect in distributed mode with multiple workers:

```properties
# Distributed mode ensures task redistribution on worker failure
group.id=connect-cluster
```

Workers automatically rebalance connectors, but in-flight messages are still lost.

#### 6. Implement Downstream Gap Detection

In your consumer application, detect and alert on data gaps:

```python
# Python consumer example
last_timestamp = None
for message in consumer:
    current_timestamp = parse_timestamp(message.value)
    if last_timestamp and (current_timestamp - last_timestamp) > threshold:
        alert("Data gap detected: {} seconds".format(current_timestamp - last_timestamp))
    last_timestamp = current_timestamp
```

#### 7. Fast Reconnection Configuration

Minimize downtime with aggressive reconnection:

```properties
# Reconnect every 1 second (if server allows)
websocket.reconnect.enabled=true
websocket.reconnect.interval.ms=1000
websocket.connection.timeout.ms=5000
```

Warning: Too aggressive can trigger rate limiting on some servers.

### Disaster Recovery Guidance

When data loss occurs, you **cannot** recover lost WebSocket messages. However, you can:

1. **Detect the Gap**
   - Monitor connector logs for disconnection timestamps
   - Correlate with Kafka topic message timestamps
   - Identify the exact time window of data loss

2. **Quantify Impact**
   ```bash
   # Check connector logs for queue size at failure
   grep "Queue utilization" connect.log | tail -1
   # Output: Queue utilization: 8542/10000 (85.42%)
   # Impact: ~8500 messages lost
   ```

3. **Alternative Data Sources**
   - Check if the WebSocket provider has a REST API for historical data
   - Example: Binance provides REST API to fetch historical trades
   - Backfill the gap using the REST API if available

4. **Document and Alert**
   - Log the incident with exact time ranges
   - Alert stakeholders about data quality impact
   - Update downstream systems about the gap

### Production Recommendations

For production deployments requiring data durability:

1. **Accept the Trade-off**: Understand that some data loss is inherent to WebSocket streaming
2. **Monitor Aggressively**: Set up comprehensive alerting on connection status and queue utilization
3. **Redundancy**: Deploy multiple connectors when data is critical
4. **Downstream Validation**: Implement gap detection in consuming applications
5. **Alternative Sources**: Have REST API backfill strategies for critical data
6. **Document SLAs**: Set realistic data completeness expectations with stakeholders

### When Not to Use This Connector

Consider alternative solutions if:
- **Zero data loss** is required → Use REST API polling with offset tracking
- **Exactly-once semantics** needed → WebSocket is fundamentally incompatible
- **Historical replay** required → WebSocket doesn't support this
- **Audit trail** mandatory → Ephemeral nature prevents auditing

## Troubleshooting

### Common Errors Reference Table

| Error Message | Root Cause | Solution |
|--------------|------------|----------|
| `ClassNotFoundException: io.conduktor.connect.websocket.WebSocketSourceConnector` | Connector JAR not in plugin path | Verify plugin directory and `plugin.path` configuration |
| `NoClassDefFoundError: okhttp3/WebSocket` | Missing OkHttp dependency | Add OkHttp JAR to plugin directory or use uber JAR |
| `ConnectException: Failed to connect to [host]` | Network/firewall blocking connection | Check network connectivity, firewall rules, proxy settings |
| `ProtocolException: Expected HTTP 101 response but was '403'` | Authentication failure | Verify auth token, check headers, review API credentials |
| `SocketTimeoutException: timeout` | Connection timeout | Increase `websocket.connection.timeout.ms`, check network latency |
| `SSLHandshakeException: PKIX path building failed` | SSL certificate validation failed | Add certificate to truststore or configure SSL properly |
| `IllegalArgumentException: Subscription message is not valid JSON` | Malformed subscription message | Validate JSON syntax, escape special characters |
| `Queue full, dropping oldest message` | Queue overflow from high throughput | Increase `websocket.message.queue.size` or optimize Kafka producer |
| `Connection closed by server: 1008` | Policy violation (rate limit, auth) | Check server rate limits, verify authentication, review subscription |
| `Connection closed by server: 1003` | Unsupported data type | Check message format expectations, verify subscription message |

### Connection Issues

#### Problem: Connector Cannot Establish Connection

**Symptoms:**
```
[WebSocketSourceTask] Failed to connect to wss://example.com/ws
java.net.ConnectException: Failed to connect to example.com/443
```

**Diagnostic Steps:**

1. **Test WebSocket endpoint directly:**
```bash
# Using wscat (install: npm install -g wscat)
wscat -c wss://stream.binance.com:9443/ws

# Or using curl (for initial HTTP handshake)
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
  https://stream.binance.com:9443/ws
```

2. **Check network connectivity:**
```bash
# Test DNS resolution
nslookup stream.binance.com

# Test port accessibility
telnet stream.binance.com 9443

# Test through proxy if applicable
curl -x http://proxy.example.com:8080 https://stream.binance.com:9443
```

3. **Review connector logs for details:**
```bash
grep -A 10 "Failed to connect" $KAFKA_HOME/logs/connect.log
```

**Solutions:**

- **Firewall blocking**: Open outbound port 443 (wss) or 80 (ws)
- **Proxy required**: Configure JVM proxy settings in Connect startup script:
  ```bash
  export KAFKA_OPTS="-Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080"
  ```
- **VPN required**: Ensure VPN connection is active before starting connector
- **URL incorrect**: Verify exact WebSocket endpoint from API documentation

#### Problem: SSL/TLS Certificate Errors

**Symptoms:**
```
javax.net.ssl.SSLHandshakeException: PKIX path building failed
sun.security.validator.ValidatorException: PKIX path building failed:
  sun.security.provider.certpath.SunCertPathBuilderException:
  unable to find valid certification path to requested target
```

**Diagnostic Steps:**

1. **Download and inspect certificate:**
```bash
# Get certificate
echo | openssl s_client -servername stream.binance.com \
  -connect stream.binance.com:9443 2>/dev/null | \
  openssl x509 -noout -text

# Save certificate
echo | openssl s_client -servername stream.binance.com \
  -connect stream.binance.com:9443 2>/dev/null | \
  sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > server.crt
```

2. **Check Java truststore:**
```bash
keytool -list -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit | grep -i binance
```

**Solutions:**

**Option 1: Add Certificate to Java Truststore (Production)**
```bash
# Import certificate
sudo keytool -import -trustcacerts -alias binance-ws \
  -file server.crt \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -storepass changeit

# Restart Kafka Connect
systemctl restart kafka-connect
```

**Option 2: Create Custom Truststore**
```bash
# Create new truststore
keytool -import -trustcacerts -alias binance-ws \
  -file server.crt \
  -keystore /etc/kafka/truststore.jks \
  -storepass changeit -noprompt

# Configure Kafka Connect to use it
export KAFKA_OPTS="-Djavax.net.ssl.trustStore=/etc/kafka/truststore.jks \
                   -Djavax.net.ssl.trustStorePassword=changeit"
```

**Option 3: Disable Certificate Validation (TESTING ONLY)**
```bash
# WARNING: Only for development/testing
export KAFKA_OPTS="-Djavax.net.ssl.trustStore=NONE"
```

#### Problem: Authentication Failures

**Symptoms:**
```
[WebSocketSourceTask] Connection failed: Response{protocol=http/1.1, code=403}
Expected HTTP 101 response but was '403 Forbidden'
```

**Diagnostic Steps:**

1. **Test authentication with curl:**
```bash
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: SGVsbG8sIHdvcmxkIQ==" \
  https://api.example.com/ws
```

2. **Verify token format:**
```bash
# Decode JWT token (if using JWT)
echo "YOUR_TOKEN" | cut -d'.' -f2 | base64 -d | jq .
```

**Solutions:**

- **Token expired**: Refresh authentication token and update connector config
- **Token format wrong**: Ensure correct format (Bearer vs API-Key vs custom)
  ```json
  {
    "websocket.auth.token": "YOUR_TOKEN"
  }
  ```
  Results in: `Authorization: Bearer YOUR_TOKEN`

- **Custom header needed**: Use `websocket.headers` instead:
  ```json
  {
    "websocket.headers": "X-API-Key:YOUR_TOKEN,X-API-Secret:YOUR_SECRET"
  }
  ```

- **Header case sensitivity**: Some servers require exact header casing

### Subscription Message Issues

#### Problem: Subscription Message Rejected

**Symptoms:**
```
[WebSocketSourceTask] Connection closed by server: code=1003, reason=Unsupported data
```

**Diagnostic Steps:**

1. **Validate JSON syntax:**
```bash
echo '{"method":"SUBSCRIBE","params":["btcusdt@trade"],"id":1}' | jq .
```

2. **Test subscription interactively:**
```bash
wscat -c wss://stream.binance.com:9443/ws
# Then manually type subscription message
```

3. **Check server documentation:**
- Review exact subscription message format required
- Verify parameter names and casing
- Check required fields

**Solutions:**

- **JSON syntax error**: Use JSON validator, check for:
  - Unescaped quotes inside strings
  - Missing commas or brackets
  - Trailing commas (not allowed in JSON)

- **Escape quotes properly in properties file:**
  ```properties
  # Correct
  websocket.subscription.message={"method":"SUBSCRIBE","params":["btcusdt@trade"],"id":1}

  # Wrong (don't escape in properties file)
  websocket.subscription.message={\"method\":\"SUBSCRIBE\"}
  ```

- **Wrong parameter names**: Check API documentation for exact field names
  ```json
  // Binance uses "method" and "params"
  {"method":"SUBSCRIBE","params":["btcusdt@trade"],"id":1}

  // Coinbase uses "type" and "channels"
  {"type":"subscribe","product_ids":["BTC-USD"],"channels":["level2"]}
  ```

### Queue Overflow Issues

#### Problem: Messages Being Dropped

**Symptoms:**
```
[WebSocketSourceTask] Queue full (10000/10000), dropping oldest message
[WebSocketSourceTask] Dropped 1523 messages in last 30 seconds
```

**Diagnostic Steps:**

1. **Monitor queue utilization over time:**
```bash
tail -f connect.log | grep "Queue utilization"
```

2. **Check Kafka producer lag:**
```bash
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group connect-websocket-source --describe
```

3. **Measure message rate:**
```bash
# Count messages per second
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic websocket-messages --from-beginning | pv -l > /dev/null
```

**Solutions:**

**Short-term: Increase Queue Size**
```properties
# Increase to 50000 for high-throughput streams
websocket.message.queue.size=50000
```

**Medium-term: Optimize Kafka Producer**
```properties
# In connect-distributed.properties
producer.linger.ms=10
producer.batch.size=32768
producer.compression.type=lz4
producer.acks=1
```

**Long-term: Scale Infrastructure**
- Add more Kafka brokers to increase throughput
- Increase partition count for target topic
- Allocate more resources to Connect worker (CPU/memory)
- Use SSD storage for Kafka broker data directories

### Reconnection Problems

#### Problem: Reconnection Loop (Repeated Failures)

**Symptoms:**
```
[WebSocketSourceTask] Reconnection attempt 1 failed
[WebSocketSourceTask] Reconnection attempt 2 failed
[WebSocketSourceTask] Reconnection attempt 3 failed
...
```

**Diagnostic Steps:**

1. **Check server availability:**
```bash
# Test if server is up
curl -I https://stream.binance.com:9443

# Check for planned maintenance
# Review server status page or Twitter/status feeds
```

2. **Review reconnection interval:**
```bash
grep "reconnect.interval" config/websocket-connector.json
```

3. **Check for rate limiting:**
```bash
# Look for 429 responses
grep "429" connect.log
grep "rate limit" connect.log
```

**Solutions:**

- **Server down**: Wait for server recovery, no action needed (auto-reconnect will succeed)

- **Rate limited**: Increase reconnection interval
  ```properties
  websocket.reconnect.interval.ms=30000  # 30 seconds instead of 5
  ```

- **Authentication token expired**: Update token in connector config
  ```bash
  # Update running connector
  curl -X PUT http://localhost:8083/connectors/websocket-connector/config \
    -H "Content-Type: application/json" \
    -d '{"websocket.auth.token":"NEW_TOKEN",...}'
  ```

- **Subscription message wrong**: Fix subscription format (see Subscription Message Issues)

- **IP banned**: Contact API provider, may need to whitelist IP or rotate IPs

### Dependency and ClassPath Issues

#### Problem: NoClassDefFoundError for OkHttp Classes

**Symptoms:**
```
java.lang.NoClassDefFoundError: okhttp3/WebSocket
java.lang.NoClassDefFoundError: okio/ByteString
java.lang.NoClassDefFoundError: kotlin/jvm/internal/Intrinsics
```

**Diagnostic Steps:**

1. **Check plugin directory contents:**
```bash
ls -lh $KAFKA_HOME/plugins/kafka-connect-websocket/
```

2. **Verify JAR contents:**
```bash
jar tf kafka-connect-websocket-1.0.0.jar | grep okhttp
```

**Solutions:**

**If using regular JAR (not uber JAR):**
```bash
# Add all dependencies to plugin directory
cd /path/to/project
mvn dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory=$KAFKA_HOME/plugins/kafka-connect-websocket/
```

**If using uber JAR:**
```bash
# Rebuild with dependencies included
mvn clean package shade:shade
cp target/kafka-connect-websocket-1.0.0-shaded.jar \
  $KAFKA_HOME/plugins/kafka-connect-websocket/
```

**Verify all required JARs are present:**
- kafka-connect-websocket-1.0.0.jar
- okhttp-4.12.0.jar
- okio-3.x.x.jar
- kotlin-stdlib-1.x.x.jar

### Performance Degradation

#### Problem: Increasing Lag in Message Processing

**Symptoms:**
- Queue utilization steadily increasing
- Growing lag between WebSocket receipt and Kafka publish
- Slower consumer processing

**Diagnostic Steps:**

1. **Monitor JVM metrics:**
```bash
# Connect to JMX
jconsole localhost:9999  # If JMX enabled

# Check heap usage
jstat -gc <kafka-connect-pid> 1000
```

2. **Check Kafka broker performance:**
```bash
kafka-broker-api-versions.sh --bootstrap-server localhost:9092
```

**Solutions:**

- **Increase heap size:**
  ```bash
  export KAFKA_HEAP_OPTS="-Xmx4G -Xms4G"
  ```

- **Enable G1GC:**
  ```bash
  export KAFKA_JVM_PERFORMANCE_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
  ```

- **Tune Kafka topic:**
  ```bash
  # Increase partition count
  kafka-topics.sh --bootstrap-server localhost:9092 \
    --alter --topic websocket-messages --partitions 10
  ```

### Getting Help

When reporting issues, include:

1. **Connector configuration** (redact sensitive tokens)
2. **Full error stack trace** from connect.log
3. **Kafka Connect version**: `kafka-topics.sh --version`
4. **Connector version**: Check manifest in JAR or logs
5. **WebSocket server details** (URL, API provider)
6. **Network environment** (proxy, firewall, VPN)
7. **Reproduction steps**

**Logs to collect:**
```bash
# Connector logs
tail -1000 $KAFKA_HOME/logs/connect.log > connect.log.txt

# Connector status
curl http://localhost:8083/connectors/websocket-connector/status > status.json

# JVM info
java -version > jvm-info.txt
echo $KAFKA_OPTS >> jvm-info.txt
```

## Development

### Running Tests

Run unit tests:
```bash
mvn test
```

Run integration tests:
```bash
mvn verify
```

### Project Structure

```
src/main/java/io/conduktor/connect/websocket/
├── WebSocketSourceConnector.java         # Main connector
├── WebSocketSourceTask.java              # Task implementation
├── WebSocketClient.java                  # WebSocket client
├── WebSocketSourceConnectorConfig.java   # Configuration
└── VersionUtil.java                      # Version management

src/test/java/io/conduktor/connect/websocket/
├── WebSocketSourceConnectorTest.java     # Unit tests
├── WebSocketSourceConnectorConfigTest.java
└── WebSocketSourceTaskIT.java            # Integration tests
```

## License

Apache License 2.0 - see [LICENSE](LICENSE) file for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## Support

For issues, questions, or contributions:
- Open an issue on GitHub
- Contact: Conduktor

## Changelog

### Version 1.0.0
- Initial release
- WebSocket source connector with reconnection support
- Support for subscription messages
- Custom headers and authentication
- Comprehensive testing
