# Frequently Asked Questions

Common questions and answers about the Kafka Connect WebSocket connector.

## General Questions

### What is this connector used for?

The Kafka Connect WebSocket connector streams real-time data from WebSocket endpoints into Kafka topics. It's ideal for:

- Cryptocurrency exchange data (Binance, Coinbase, Kraken)
- IoT sensor data streams
- Financial market data feeds
- Live collaboration tools (chat, presence updates)
- Gaming platform events
- Any service exposing a WebSocket API

### How does it differ from a custom consumer?

| Feature | This Connector | Custom Consumer |
|---------|---------------|-----------------|
| **Integration** | Native Kafka Connect | Requires custom code |
| **Deployment** | Kafka Connect framework | Separate service/application |
| **Monitoring** | Built-in JMX metrics | Manual implementation |
| **Reconnection** | Automatic with backoff | Custom implementation |
| **Configuration** | JSON config files | Code changes |
| **Scalability** | Kafka Connect clustering | DIY clustering |

### Operational Features

The connector includes:

- Comprehensive error handling and automatic reconnection
- JMX metrics with Prometheus/Grafana integration
- Operational runbooks (see `docs/operations/RUNBOOK.md`)
- 91 tests covering integration, resources, offsets, and edge cases
- Extensive logging and troubleshooting guides

**⚠️ Important: At-Most-Once Delivery Semantics**

This connector provides at-most-once delivery due to architectural limitations (in-memory buffering, no WebSocket replay capability). Messages can be lost during shutdowns, crashes, or network failures. Best suited for telemetry, monitoring, and scenarios where occasional data loss is acceptable. See the README for detailed data loss scenarios and mitigation strategies.

## Installation & Setup

### Do I need to build from source?

Yes, currently you need to build from source using Maven:

```bash
mvn clean package
```

A pre-built JAR distribution is planned for future releases.

### Which Kafka version do I need?

**Minimum:** Kafka 3.9.0
**Recommended:** Latest stable Kafka version

The connector uses Kafka Connect API features available in 3.9.0+.

### Can I use this with Confluent Platform?

Yes, the connector works with:

- Apache Kafka (open source)
- Confluent Platform
- Amazon MSK (Managed Streaming for Kafka)
- Azure Event Hubs for Kafka
- Any Kafka-compatible platform supporting Connect API

### Where should I install the connector JAR?

Install in the Kafka Connect plugin directory:

```bash
# Default locations
/usr/local/share/kafka/plugins/kafka-connect-websocket/
# or
$KAFKA_HOME/plugins/kafka-connect-websocket/
```

Ensure `plugin.path` in `connect-distributed.properties` includes this location.

### Do I need separate dependency JARs?

**Option 1 (Recommended):** Build an uber JAR with dependencies included:
```bash
mvn clean package shade:shade
```

**Option 2:** Copy dependencies separately:
```bash
mvn dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory=$KAFKA_HOME/plugins/kafka-connect-websocket/
```

Required dependencies: OkHttp (4.12.0), Okio, Kotlin stdlib.

## Configuration

### What's the minimum configuration?

Only two parameters are required:

```json
{
  "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
  "websocket.url": "wss://example.com/ws",
  "kafka.topic": "websocket-messages"
}
```

### How do I authenticate to secured WebSocket endpoints?

Use `websocket.auth.token` for Bearer authentication:

```json
{
  "websocket.auth.token": "your-api-token"
}
```

This automatically adds: `Authorization: Bearer your-api-token`

For custom authentication schemes, use `websocket.headers`:

```json
{
  "websocket.headers": "X-API-Key:your-key,X-API-Secret:your-secret"
}
```

### How do I subscribe to specific channels?

Use `websocket.subscription.message` to send a subscription after connection:

```json
{
  "websocket.subscription.message": "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}"
}
```

This message is sent immediately after the WebSocket connection opens.

### Can I connect to multiple WebSocket endpoints?

No, each connector instance connects to one WebSocket endpoint. To stream from multiple endpoints:

1. Create separate connector instances
2. Use different connector names
3. Route to the same or different Kafka topics

Example:
```bash
# Connector 1: Binance BTC/USDT
curl -X POST http://localhost:8083/connectors \
  -d '{"name":"binance-btcusdt", "config":{...}}'

# Connector 2: Binance ETH/USDT
curl -X POST http://localhost:8083/connectors \
  -d '{"name":"binance-ethusdt", "config":{...}}'
```

### What happens if my subscription message is rejected?

The connector will log an error and continue reconnecting. Common causes:

- **Invalid JSON syntax** - Validate with `jq` or online tools
- **Wrong subscription format** - Check API documentation
- **Missing authentication** - Ensure auth token is correct
- **Rate limiting** - Reduce reconnection frequency

Check logs for specific error messages.

## Data & Reliability

### What delivery guarantees does this connector provide?

**At-most-once** delivery semantics:

- Messages can be lost (e.g., during crashes)
- Messages are never duplicated
- No offset tracking (WebSocket protocol limitation)

See Data Reliability section in the README for detailed information.

### Will I lose data if the connector crashes?

**Yes.** Messages in the in-memory queue (up to `websocket.message.queue.size`) are lost on:

- Connector shutdown
- JVM crash
- Kafka Connect worker failure
- Network interruptions

Mitigation strategies: Data Loss Scenarios in the README

### Can I replay historical WebSocket data?

**No.** WebSocket protocol limitations:

- No concept of offsets or message positions
- Server doesn't store historical messages
- Cannot "rewind" after disconnection

Alternative: Use REST API for historical backfill (if available).

### What data format does the connector produce?

Messages are produced as **strings** (UTF-8 encoded text):

- Schema: `STRING`
- Value: Raw WebSocket message content
- Key: `null` (no message key)

Example Kafka message:
```json
{
  "topic": "websocket-messages",
  "partition": 0,
  "offset": 12345,
  "key": null,
  "value": "{\"e\":\"trade\",\"s\":\"BTCUSDT\",\"p\":\"50000.00\"}"
}
```

For structured processing, use Kafka Streams or ksqlDB to parse JSON downstream.

## Operations

### How do I monitor the connector?

Three monitoring approaches:

1. **Built-in Logging** (easiest):
   ```bash
   tail -f $KAFKA_HOME/logs/connect.log | grep WebSocketSourceTask
   ```

2. **JMX Metrics** (recommended):
   - Enable JMX in Kafka Connect
   - Use JConsole, VisualVM, or Prometheus JMX Exporter

3. **Kafka Connect REST API**:
   ```bash
   curl http://localhost:8083/connectors/websocket-connector/status
   ```

See Monitoring section in the README for detailed setup.

### What metrics should I alert on?

Critical alerts:

- **Connector down** - `connector.state != RUNNING`
- **No messages received** - Zero throughput for > 5 minutes
- **Queue overflow** - Queue utilization > 80%
- **High reconnection rate** - > 5 reconnects per hour

Warning alerts:

- **Low throughput** - < 50% of expected rate
- **Processing latency** - Poll batch time > 1 second

### How do I troubleshoot connection failures?

Common diagnostics:

1. **Test endpoint directly**:
   ```bash
   wscat -c wss://stream.binance.com:9443/ws
   ```

2. **Check network connectivity**:
   ```bash
   telnet stream.binance.com 9443
   ```

3. **Review connector logs**:
   ```bash
   grep "Failed to connect" $KAFKA_HOME/logs/connect.log
   ```

4. **Verify authentication**:
   ```bash
   curl -i -H "Authorization: Bearer TOKEN" https://api.example.com/
   ```

See Troubleshooting section in the README for detailed solutions.

### How do I update connector configuration?

**For running connectors:**

```bash
curl -X PUT http://localhost:8083/connectors/websocket-connector/config \
  -H "Content-Type: application/json" \
  -d @updated-config.json
```

The connector will restart automatically with the new configuration.

**Configuration changes requiring restart:**
- `websocket.url`
- `websocket.auth.token`
- `websocket.subscription.message`

**Configuration changes without restart:**
- `kafka.topic` (new messages only)

### Can I pause and resume the connector?

Yes, use the Kafka Connect REST API:

**Pause:**
```bash
curl -X PUT http://localhost:8083/connectors/websocket-connector/pause
```

**Resume:**
```bash
curl -X PUT http://localhost:8083/connectors/websocket-connector/resume
```

**Note:** Pausing closes the WebSocket connection. Messages sent during the pause are lost.

## Performance

### What throughput can I expect?

Typical performance on standard hardware (4 CPU, 8 GB RAM):

- **Message rate**: 10,000+ messages/second
- **Latency**: < 10ms from WebSocket receipt to Kafka produce
- **Queue capacity**: Configurable (default: 10,000 messages)

Actual throughput depends on:
- Message size
- Kafka broker performance
- Network latency
- Queue configuration

### How many tasks can I run per connector?

**Always 1 task** per connector (WebSocket limitation).

WebSocket connections are single-threaded by protocol design. Each connector maintains one WebSocket connection.

To parallelize:
- Create multiple connector instances
- Each connects to a different endpoint or subscription

### What's the memory footprint?

Memory usage depends on queue size and message size:

**Formula:**
```
Memory ≈ queue_size × avg_message_size × 2
```

**Example:**
- Queue size: 10,000 messages
- Average message: 1 KB
- Memory: ~20 MB (with overhead)

**Recommendation:**
- Development: 512 MB heap
- Production: 2 GB heap

### How do I optimize throughput?

1. **Increase queue size** (handle bursts):
   ```properties
   websocket.message.queue.size=50000
   ```

2. **Optimize Kafka producer** (in `connect-distributed.properties`):
   ```properties
   producer.linger.ms=10
   producer.batch.size=32768
   producer.compression.type=lz4
   ```

3. **Increase partitions** (for target topic):
   ```bash
   kafka-topics.sh --alter --topic websocket-messages --partitions 10
   ```

4. **Scale Kafka brokers** (for cluster throughput)

## Development

### Can I contribute to this project?

Yes! Contributions are welcome:

1. Fork the repository
2. Create a feature branch
3. Submit a pull request

See Contributing section in the README for details.

### How do I run tests locally?

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Specific test
mvn test -Dtest=WebSocketSourceConnectorTest
```

### How do I build for a different Kafka version?

Edit `pom.xml` and change the Kafka version:

```xml
<properties>
  <kafka.version>3.9.0</kafka.version>  <!-- Change this -->
</properties>
```

Then rebuild:
```bash
mvn clean package
```

## Troubleshooting

### Why isn't my connector appearing in the plugin list?

**Check:**

1. JAR is in the correct plugin directory
2. `plugin.path` is configured in `connect-distributed.properties`
3. Kafka Connect was restarted after installation
4. All dependencies (OkHttp, Okio, Kotlin stdlib) are present

**Verify:**
```bash
ls -lh $KAFKA_HOME/plugins/kafka-connect-websocket/
curl http://localhost:8083/connector-plugins | jq
```

### Why do I get "ClassNotFoundException"?

**Cause:** Missing connector JAR or wrong plugin path.

**Solution:**
```bash
# Verify plugin directory
echo $KAFKA_HOME/plugins/kafka-connect-websocket/

# Check connect-distributed.properties
grep plugin.path config/connect-distributed.properties

# Restart Kafka Connect
systemctl restart kafka-connect
```

### Why do I get "NoClassDefFoundError: okhttp3/WebSocket"?

**Cause:** Missing OkHttp dependency.

**Solution:**
```bash
# Option 1: Use uber JAR
mvn clean package shade:shade

# Option 2: Copy dependencies
mvn dependency:copy-dependencies -DincludeScope=runtime \
  -DoutputDirectory=$KAFKA_HOME/plugins/kafka-connect-websocket/
```

### Why is my queue constantly full?

**Cause:** Kafka producer throughput < WebSocket message rate.

**Solutions:**

1. **Increase queue size** (temporary):
   ```properties
   websocket.message.queue.size=50000
   ```

2. **Optimize Kafka producer** (permanent):
   ```properties
   producer.linger.ms=10
   producer.batch.size=32768
   producer.compression.type=lz4
   ```

3. **Scale Kafka infrastructure**:
   - Add more brokers
   - Increase partition count
   - Use SSD storage

See the Troubleshooting section in the README for details on queue overflow issues.

## Compatibility

### Does this work with Kafka 2.x?

No, minimum Kafka version is 3.9.0. The connector uses APIs introduced in Kafka 3.x.

### Does this work with Java 8?

No, minimum Java version is 11. The connector uses Java 11 language features.

### Does this work with Kubernetes?

Yes, deploy Kafka Connect in Kubernetes and include this connector in the plugin directory. Common approaches:

- **Strimzi Kafka Operator** - Build custom Connect image with connector
- **Confluent for Kubernetes** - Use custom Connect image
- **Helm Charts** - Mount plugin directory via ConfigMap/PersistentVolume

### Does this work with Docker?

Yes, create a custom Kafka Connect Docker image:

```dockerfile
FROM confluentinc/cp-kafka-connect:7.5.0

# Copy connector and dependencies
COPY kafka-connect-websocket-1.0.0.jar /usr/share/confluent-hub-components/kafka-connect-websocket/
COPY okhttp-4.12.0.jar /usr/share/confluent-hub-components/kafka-connect-websocket/
COPY okio-*.jar /usr/share/confluent-hub-components/kafka-connect-websocket/
COPY kotlin-stdlib-*.jar /usr/share/confluent-hub-components/kafka-connect-websocket/
```

## Still Have Questions?

- **GitHub Issues**: [Open an issue](https://github.com/conduktor/kafka-connect-websocket/issues)
- **Slack Community**: [Join Conduktor Slack](https://conduktor.io/slack)
- **Documentation**: [Browse documentation](https://conduktor.github.io/kafka-connect-websocket/)
