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

1. Copy the JAR file to your Kafka Connect plugins directory:
```bash
cp target/kafka-connect-websocket-1.0.0.jar $KAFKA_HOME/plugins/
```

2. Restart Kafka Connect to load the connector.

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

The connector logs metrics every 30 seconds including:
- Connection status
- Messages received
- Records produced to Kafka
- Reconnection attempts

Check the logs:
```bash
tail -f $KAFKA_HOME/logs/connect.log
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

## Troubleshooting

### Connection Issues

If the connector can't connect:
1. Verify the WebSocket URL is correct and accessible
2. Check firewall/network settings
3. Ensure proper authentication headers if required
4. Review connector logs for detailed error messages

### Message Loss

To minimize message loss:
- Increase `websocket.message.queue.size` for high-throughput streams
- Monitor queue utilization in logs
- Ensure Kafka Connect has sufficient resources

### Reconnection Problems

If reconnection fails repeatedly:
1. Check the WebSocket server's availability
2. Increase `websocket.reconnect.interval.ms`
3. Verify subscription message format if required
4. Check authentication token expiration

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
