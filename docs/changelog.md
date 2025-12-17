# Changelog

All notable changes to the Kafka Connect WebSocket Source Connector will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Planned Features

- [ ] Configurable message transformations (SMTs)
- [ ] Dead letter queue support for failed messages
- [ ] Compression support for Kafka producer
- [ ] Metrics dashboard templates (Grafana)
- [ ] Helm chart for Kubernetes deployments

## [1.0.0] - 2025-12-17

### Added

#### Core Features
- Initial release of Kafka Connect WebSocket Source Connector
- Support for WebSocket (ws://) and WebSocket Secure (wss://) protocols
- Automatic reconnection with configurable intervals
- Subscription message support for WebSocket APIs
- Bearer token authentication via `websocket.auth.token` parameter
- Custom HTTP headers support via `websocket.headers` parameter
- Configurable message queue with overflow handling
- Comprehensive logging with periodic metrics

#### Configuration
- Required parameters: `websocket.url`, `kafka.topic`
- Optional parameters:
  - `websocket.subscription.message` - JSON subscription message
  - `websocket.reconnect.enabled` - Enable/disable reconnection (default: true)
  - `websocket.reconnect.interval.ms` - Reconnection interval (default: 5000ms)
  - `websocket.headers` - Custom headers (format: key1:value1,key2:value2)
  - `websocket.auth.token` - Bearer token for Authorization header
  - `websocket.message.queue.size` - Maximum queue size (default: 10000)
  - `websocket.connection.timeout.ms` - Connection timeout (default: 30000ms)

#### Monitoring & Operations
- JMX metrics exposure via Kafka Connect framework
- Built-in metrics logging every 30 seconds:
  - Connection status (OPEN/CLOSED/CONNECTING)
  - Messages received count
  - Records produced to Kafka count
  - Queue utilization (current/max percentage)
  - Reconnection attempt counter
- Integration with Prometheus via JMX Exporter
- Sample Grafana dashboard configuration
- Health check script examples

#### Documentation
- Comprehensive README with 1,383 lines
- Quick start guide with multiple examples:
  - WebSocket echo server (testing)
  - Binance cryptocurrency trades
  - Coinbase Level 2 order book
  - Authenticated WebSocket APIs
- Detailed troubleshooting section with error reference table
- Architecture documentation with data flow diagram
- Data reliability guide covering at-most-once semantics
- Production deployment recommendations

#### Testing
- Unit tests for connector and configuration
- Integration tests with mock WebSocket server
- Configuration validation tests (35 test cases)
- Test coverage report documenting all scenarios

#### Dependencies
- Apache Kafka Connect API 3.9.0
- OkHttp WebSocket client 4.12.0
- SLF4J logging API 1.7.36
- JUnit 5.9.2 (testing)
- Mockito 5.2.0 (testing)

### Technical Details

#### Architecture
- **WebSocketSourceConnector**: Main connector class managing configuration and task lifecycle
- **WebSocketSourceTask**: Task implementation handling WebSocket connection and message polling
- **WebSocketClient**: OkHttp-based WebSocket client with reconnection logic
- **WebSocketSourceConnectorConfig**: Configuration definition with validation

#### Data Flow
```
WebSocket Endpoint → OkHttp Client → LinkedBlockingDeque Queue →
SourceTask.poll() → SourceRecord → Kafka Topic
```

#### Limitations Documented
- Single task per connector (WebSocket protocol constraint)
- At-most-once delivery semantics
- No offset management (WebSocket limitation)
- Messages consumed as strings only
- In-memory queue data lost on shutdown

### Known Issues

- None reported in initial release

### Breaking Changes

N/A - Initial release

---

## Version History Format

### [X.Y.Z] - YYYY-MM-DD

#### Added
Features or capabilities that were added in this release.

#### Changed
Changes in existing functionality or behavior.

#### Deprecated
Features that will be removed in future releases.

#### Removed
Features that were removed in this release.

#### Fixed
Bug fixes and error corrections.

#### Security
Security vulnerability fixes and improvements.

---

## Upgrade Guide

### From Pre-Release to 1.0.0

This is the initial stable release. No migration required.

### Future Upgrades

Upgrade instructions will be provided here for future releases.

---

## Support Policy

### Version Support

- **Latest stable version**: Full support with bug fixes and security updates
- **Previous minor version**: Security fixes only
- **Older versions**: Community support via GitHub Issues

### Compatibility Matrix

| Connector Version | Min Kafka Version | Max Kafka Version | Java Version |
|-------------------|-------------------|-------------------|--------------|
| 1.0.0 | 3.9.0 | Latest | 11+ |

---

## Release Notes Archive

### Release Highlights

#### 1.0.0 - Initial Stable Release (2025-12-17)

**🎉 First Production-Ready Release**

The Kafka Connect WebSocket Source Connector is now production-ready with comprehensive features for streaming real-time data from any WebSocket endpoint into Apache Kafka.

**Key Capabilities:**
- Stream data from cryptocurrency exchanges (Binance, Coinbase, Kraken)
- Connect to IoT sensor streams and telemetry data
- Integrate with financial market data feeds
- Support for authenticated and custom WebSocket APIs

**Production Features:**
- Automatic reconnection with configurable backoff
- Built-in monitoring via JMX metrics
- Comprehensive error handling and logging
- Prometheus/Grafana integration support

**Getting Started:**
```bash
mvn clean package
cp target/kafka-connect-websocket-1.0.0.jar $KAFKA_HOME/plugins/kafka-connect-websocket/
```

**Example Configuration:**
```json
{
  "name": "binance-btcusdt",
  "config": {
    "connector.class": "io.conduktor.connect.websocket.WebSocketSourceConnector",
    "websocket.url": "wss://stream.binance.com:9443/ws",
    "kafka.topic": "binance-trades",
    "websocket.subscription.message": "{\"method\":\"SUBSCRIBE\",\"params\":[\"btcusdt@trade\"],\"id\":1}"
  }
}
```

**Important Notes:**
- At-most-once delivery semantics (see [Data Reliability](reliability/index.md))
- Single task per connector (WebSocket protocol limitation)
- In-memory queue data lost on shutdown

---

## Contributing to Changelog

When submitting a PR, please update the `[Unreleased]` section with your changes under the appropriate category (Added, Changed, Fixed, etc.).

**Format:**
```markdown
### Category
- Brief description of change ([#PR_NUMBER](link))
```

**Example:**
```markdown
### Added
- Support for custom SSL certificates ([#42](https://github.com/conduktor/kafka-connect-websocket/pull/42))

### Fixed
- Memory leak in queue overflow scenario ([#38](https://github.com/conduktor/kafka-connect-websocket/pull/38))
```

---

## Links

- [GitHub Repository](https://github.com/conduktor/kafka-connect-websocket)
- [Issue Tracker](https://github.com/conduktor/kafka-connect-websocket/issues)
- [Documentation](https://conduktor.github.io/kafka-connect-websocket/)
- [Conduktor Website](https://conduktor.io)

[Unreleased]: https://github.com/conduktor/kafka-connect-websocket/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/conduktor/kafka-connect-websocket/releases/tag/v1.0.0
