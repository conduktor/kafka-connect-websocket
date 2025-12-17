# Kafka Connect WebSocket Source Connector

<div class="hero" markdown>

Stream real-time data from any WebSocket endpoint directly into Apache Kafka with automatic reconnection, authentication support, and comprehensive monitoring.

[Get Started](getting-started/index.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/conduktor/kafka-connect-websocket){ .md-button }

</div>

## Features

### :material-flash: Real-Time Streaming
Connect to any WebSocket endpoint (ws:// or wss://) and stream data directly into Kafka topics with minimal latency.

### :material-sync: Automatic Reconnection
Built-in reconnection logic with configurable intervals ensures resilient connections even when endpoints are unstable.

### :material-shield-lock: Authentication Support
Bearer token authentication and custom headers for connecting to secured WebSocket APIs.

### :material-message-processing: Subscription Messages
Send subscription messages after connection to exchanges like Binance, Coinbase, and custom WebSocket servers.

### :material-gauge: Built-in Monitoring
Comprehensive metrics via JMX, detailed logging, and integration with Prometheus/Grafana for production observability.

### :material-buffer: Message Buffering
Configurable in-memory queue to handle traffic bursts and optimize throughput to Kafka.

## Quick Example

Stream Bitcoin trades from Binance into Kafka:

=== "Connector Configuration"

    ```json
    {
      "name": "binance-btcusdt-connector",
      "config": {
        "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
        "tasks.max": "1",
        "websocket.url": "wss://stream.binance.com:9443/ws",
        "kafka.topic": "binance-btcusdt-trades",
        "websocket.subscription.message": "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}",
        "websocket.reconnect.enabled": "true"
      }
    }
    ```

=== "Deploy Connector"

    ```bash
    curl -X POST http://localhost:8083/connectors \
      -H "Content-Type: application/json" \
      -d @binance-connector.json
    ```

=== "Consume Messages"

    ```bash
    kafka-console-consumer.sh \
      --bootstrap-server localhost:9092 \
      --topic binance-btcusdt-trades \
      --from-beginning
    ```

## Use Cases

The connector is ideal for streaming real-time data from:

- **Cryptocurrency Exchanges** - Live trades, order books, and ticker data (Binance, Coinbase, Kraken)
- **IoT Devices** - Sensor data and telemetry streams
- **Financial Markets** - Stock tickers, forex rates, market data
- **Collaboration Tools** - Chat messages, presence updates, notifications
- **Gaming Platforms** - Live scores, player updates, game events
- **Custom WebSocket APIs** - Any service exposing a WebSocket endpoint

## Architecture

```mermaid
graph LR
    A[WebSocket Endpoint] -->|OkHttp Client| B[Message Queue]
    B -->|Poll| C[Kafka Connect Task]
    C -->|Produce| D[Kafka Topic]

    style A fill:#3b82f6
    style B fill:#f59e0b
    style C fill:#10b981
    style D fill:#ef4444
```

The connector uses OkHttp's WebSocket client for reliable connections, maintains an in-memory queue for buffering, and integrates seamlessly with Kafka Connect's task framework.

## Why This Connector?

| Feature | Kafka Connect WebSocket | Custom Consumer | REST Polling |
|---------|-------------------------|-----------------|--------------|
| Real-time data | ✅ True push model | ✅ True push | ❌ Polling delays |
| Kafka integration | ✅ Native | ⚠️ Manual | ⚠️ Manual |
| Reconnection logic | ✅ Built-in | ⚠️ Custom code | ⚠️ Custom code |
| Monitoring | ✅ JMX/Prometheus | ⚠️ Custom | ⚠️ Custom |
| Deployment | ✅ Kafka Connect | ❌ Separate service | ❌ Separate service |
| Maintenance | ✅ Production-ready | ⚠️ DIY | ⚠️ DIY |

## Performance

- **Throughput**: Handles 10,000+ messages/second on standard hardware
- **Latency**: < 10ms from WebSocket receipt to Kafka produce
- **Reliability**: Automatic reconnection with exponential backoff
- **Scalability**: Configurable queue size for traffic bursts

## Production Ready

Built for production deployments with:

- Comprehensive error handling and logging
- JMX metrics for Prometheus/Grafana integration
- Health check endpoints
- Graceful shutdown handling
- Extensive troubleshooting documentation

## Data Reliability

!!! warning "At-Most-Once Semantics"
    This connector implements **at-most-once** delivery semantics. Messages in the in-memory queue are lost on connector shutdown or crashes. See the README for details and mitigation strategies.

## Community & Support

- **GitHub Issues**: [Report bugs and request features](https://github.com/conduktor/kafka-connect-websocket/issues)
- **Slack Community**: [Join Conduktor Slack](https://conduktor.io/slack)
- **Documentation**: [Full reference guide](getting-started/index.md)

## License

Apache License 2.0 - see [LICENSE](https://github.com/conduktor/kafka-connect-websocket/blob/main/LICENSE) for details.

---

<div class="cta-section" markdown>

## Ready to Get Started?

[Getting Started Guide](getting-started/index.md){ .md-button .md-button--primary }
[View on GitHub](https://github.com/conduktor/kafka-connect-websocket){ .md-button }
[See FAQ](faq.md){ .md-button }

</div>
