# Getting Started

This guide will help you install, configure, and deploy your first Kafka Connect WebSocket connector.

## Overview

The Kafka Connect WebSocket Source Connector enables you to stream real-time data from any WebSocket endpoint directly into Apache Kafka topics. It handles connection management, reconnection logic, and integrates seamlessly with Kafka Connect's distributed architecture.

## What You'll Learn

In this section, you'll learn how to:

1. **[Prerequisites](prerequisites.md)** - Verify your environment has the required dependencies
2. **Installation** - Install the connector in your Kafka Connect cluster (see main README)
3. **Quick Start** - Deploy your first connector and verify it's working (see main README)

## Deployment Options

Choose the deployment method that best fits your environment:

### Distributed Mode (Production)

**Best for:**
- Production deployments
- High availability requirements
- Multiple connectors
- Horizontal scaling

**Configuration:**
```properties
# config/connect-distributed.properties
plugin.path=/usr/local/share/kafka/plugins
```

### Standalone Mode (Development)

**Best for:**
- Local development
- Testing
- Single connector instances
- Quick prototyping

**Configuration:**
```properties
# config/connect-standalone.properties
plugin.path=/usr/local/share/kafka/plugins
```

## System Requirements

### Minimum Requirements

- **Java**: 11 or higher
- **Kafka**: 3.9.0 or higher
- **Memory**: 512 MB RAM for connector
- **Network**: Access to WebSocket endpoint (outbound connections)

### Recommended for Production

- **Java**: 17 (LTS)
- **Kafka**: Latest stable version
- **Memory**: 2 GB RAM for Kafka Connect worker
- **CPU**: 2+ cores
- **Network**: Low-latency connection to WebSocket server

## Support Matrix

| Component | Minimum Version | Recommended Version | Tested Versions |
|-----------|----------------|---------------------|-----------------|
| Java | 11 | 17 | 11, 17, 21 |
| Kafka | 3.9.0 | 3.9.0+ | 3.9.0 |
| Maven | 3.6+ | 3.9+ | 3.6, 3.8, 3.9 |
| OkHttp | 4.12.0 | 4.12.0 | 4.12.0 |

## Quick Links

### :material-book-open: [Main README](https://github.com/conduktor/kafka-connect-websocket/blob/main/README.md)
Complete documentation with installation, configuration, and usage examples.

### :material-help-circle: [FAQ](../faq.md)
Frequently asked questions and troubleshooting tips.

### :material-history: [Changelog](../changelog.md)
Version history, new features, and bug fixes.

## Before You Begin

!!! tip "Check Your Environment"
    Before proceeding, ensure you have:

    - [ ] Java 11+ installed (`java -version`)
    - [ ] Kafka 3.9.0+ running
    - [ ] Access to build the connector (Maven 3.6+)
    - [ ] Network access to your target WebSocket endpoint

!!! warning "Production Considerations"
    For production deployments, review the Data Reliability section in the main README to understand the connector's at-most-once semantics and data loss scenarios.

## Next Steps

Ready to install? Start with:

1. [Prerequisites](prerequisites.md) - Verify your environment
2. [Main README](https://github.com/conduktor/kafka-connect-websocket/blob/main/README.md#installation) - Follow the installation guide
3. [Examples](https://github.com/conduktor/kafka-connect-websocket/blob/main/README.md#usage-examples) - Deploy your first connector

---

Need help? Check our [FAQ](../faq.md) or [open an issue](https://github.com/conduktor/kafka-connect-websocket/issues).
