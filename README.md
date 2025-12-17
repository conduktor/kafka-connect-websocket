# Kafka Connect WebSocket Source Connector

A Kafka Connect source connector for streaming real-time data from WebSocket endpoints into Apache Kafka topics.

**[Documentation](https://conduktor.github.io/kafka-connect-websocket)** | **[Quick Start with Docker](examples/)**

## Features

- Stream from any WebSocket endpoint (ws:// or wss://)
- Automatic reconnection with configurable intervals
- Subscription messages for exchanges (Binance, Coinbase, etc.)
- Bearer token and custom header authentication
- Configurable message buffering
- JMX metrics for monitoring

## Quick Start

### Installation

```bash
# Download the pre-built JAR
wget https://github.com/conduktor/kafka-connect-websocket/releases/download/v1.0.0/kafka-connect-websocket-1.0.0-jar-with-dependencies.jar

# Copy to Kafka Connect plugins directory
mkdir -p $KAFKA_HOME/plugins/kafka-connect-websocket
cp kafka-connect-websocket-1.0.0-jar-with-dependencies.jar $KAFKA_HOME/plugins/kafka-connect-websocket/

# Restart Kafka Connect
systemctl restart kafka-connect
```

### Deploy a Connector

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "binance-btcusdt",
    "config": {
      "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
      "tasks.max": "1",
      "websocket.url": "wss://stream.binance.com:9443/ws",
      "kafka.topic": "binance-btcusdt-trades",
      "websocket.subscription.message": "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}"
    }
  }'
```

### Verify

```bash
# Check connector status
curl http://localhost:8083/connectors/binance-btcusdt/status

# Consume messages
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic binance-btcusdt-trades --from-beginning
```

## Configuration

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `websocket.url` | Yes | - | WebSocket endpoint URL |
| `kafka.topic` | Yes | - | Target Kafka topic |
| `websocket.subscription.message` | No | null | JSON message sent after connection |
| `websocket.reconnect.enabled` | No | true | Enable automatic reconnection |
| `websocket.reconnect.interval.ms` | No | 5000 | Reconnection interval |
| `websocket.headers` | No | null | Custom headers (format: `key1:value1,key2:value2`) |
| `websocket.auth.token` | No | null | Bearer token for Authorization header |
| `websocket.message.queue.size` | No | 10000 | In-memory buffer size |
| `websocket.connection.timeout.ms` | No | 30000 | Connection timeout |

## Limitations

- **Single task per connector**: WebSocket connections are single-threaded by protocol design
- **At-most-once delivery**: Messages can be lost during shutdowns, crashes, or queue overflow
- **No replay capability**: WebSocket protocol doesn't support offset-based replay
- **In-memory buffering**: Queue contents are lost on restart

> **Note**: Kafka Connect commits offsets for this connector, but they cannot be used for replay since WebSocket servers don't support retrieving historical messages. Best suited for telemetry, monitoring, and scenarios where occasional data loss is acceptable.

## Documentation

Full documentation is available at: **[conduktor.github.io/kafka-connect-websocket](https://conduktor.github.io/kafka-connect-websocket)**

- [Getting Started Guide](https://conduktor.github.io/kafka-connect-websocket/getting-started/)
- [Configuration Reference](https://conduktor.github.io/kafka-connect-websocket/configuration/)
- [Monitoring & Operations](https://conduktor.github.io/kafka-connect-websocket/operations/)
- [Troubleshooting](https://conduktor.github.io/kafka-connect-websocket/faq/)

## Building from Source

```bash
git clone https://github.com/conduktor/kafka-connect-websocket.git
cd kafka-connect-websocket
mvn clean package
```

Output: `target/kafka-connect-websocket-1.0.0-jar-with-dependencies.jar`

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.
