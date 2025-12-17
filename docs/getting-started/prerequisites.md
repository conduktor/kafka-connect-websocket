# Prerequisites

Before installing the Kafka Connect WebSocket connector, ensure your environment meets the following requirements.

## Required Software

### Java Development Kit (JDK)

**Minimum Version:** Java 11
**Recommended:** Java 17 (LTS)

=== "Check Java Version"

    ```bash
    java -version
    ```

    Expected output:
    ```
    openjdk version "17.0.9" 2023-10-17 LTS
    OpenJDK Runtime Environment (build 17.0.9+9-LTS)
    ```

=== "Install Java (Ubuntu/Debian)"

    ```bash
    sudo apt update
    sudo apt install openjdk-17-jdk
    ```

=== "Install Java (macOS)"

    ```bash
    brew install openjdk@17
    ```

=== "Install Java (RHEL/CentOS)"

    ```bash
    sudo yum install java-17-openjdk-devel
    ```

### Apache Kafka

**Minimum Version:** 3.9.0
**Download:** [Apache Kafka Downloads](https://kafka.apache.org/downloads)

=== "Verify Kafka Installation"

    ```bash
    kafka-topics.sh --version
    ```

    Expected output:
    ```
    3.9.0 (Commit:...)
    ```

=== "Quick Kafka Setup (Local)"

    ```bash
    # Download Kafka
    wget https://downloads.apache.org/kafka/3.9.0/kafka_2.13-3.9.0.tgz
    tar -xzf kafka_2.13-3.9.0.tgz
    cd kafka_2.13-3.9.0

    # Start Kafka (KRaft mode)
    KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
    bin/kafka-storage.sh format -t $KAFKA_CLUSTER_ID -c config/kraft/server.properties
    bin/kafka-server-start.sh config/kraft/server.properties
    ```

### Maven (for building from source)

**Minimum Version:** 3.6+
**Recommended:** 3.9+

```bash
mvn --version
```

Expected output:
```
Apache Maven 3.9.5
Maven home: /usr/share/maven
Java version: 17.0.9
```

## Network Requirements

### Outbound Connectivity

The connector requires outbound access to WebSocket endpoints:

| Protocol | Port | Purpose |
|----------|------|---------|
| WebSocket (ws) | 80 | Unencrypted WebSocket connections |
| WebSocket Secure (wss) | 443 | TLS-encrypted WebSocket connections |

!!! tip "Test WebSocket Connectivity"
    ```bash
    # Install wscat (WebSocket testing tool)
    npm install -g wscat

    # Test connection
    wscat -c wss://stream.binance.com:9443/ws
    ```

### Firewall Configuration

If behind a corporate firewall, ensure WebSocket upgrade requests are allowed:

```bash
# Test with curl
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: test" \
  https://stream.binance.com:9443/ws
```

Expected response:
```
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
```

### Proxy Configuration

If using a corporate proxy:

```bash
# Set proxy environment variables
export https_proxy=http://proxy.company.com:8080
export http_proxy=http://proxy.company.com:8080

# Configure Java proxy (add to Kafka Connect startup)
export KAFKA_OPTS="-Dhttps.proxyHost=proxy.company.com \
                   -Dhttps.proxyPort=8080 \
                   -Dhttp.proxyHost=proxy.company.com \
                   -Dhttp.proxyPort=8080"
```

## Kafka Connect Setup

### Distributed Mode (Recommended for Production)

Ensure Kafka Connect is running in distributed mode:

```bash
# Check if Connect is running
curl http://localhost:8083/
```

Expected response:
```json
{
  "version": "3.9.0",
  "commit": "...",
  "kafka_cluster_id": "..."
}
```

### Internal Topics Configuration

Kafka Connect in distributed mode requires three internal topics. These are typically auto-created, but for production you should pre-create them with proper replication:

```properties
# In connect-distributed.properties
offset.storage.topic=connect-offsets
offset.storage.replication.factor=3
offset.storage.partitions=25

config.storage.topic=connect-configs
config.storage.replication.factor=3
config.storage.partitions=1

status.storage.topic=connect-status
status.storage.replication.factor=3
status.storage.partitions=5
```

!!! info "Topic Purpose"
    - **connect-offsets**: Stores source connector offsets (committed but not usable for replay with this WebSocket connector)
    - **connect-configs**: Stores connector and task configurations
    - **connect-status**: Stores connector and task status

### Producer Configuration

Configure producer settings for source connectors in `connect-distributed.properties`:

```properties
# Optimize for throughput
producer.linger.ms=10
producer.batch.size=32768
producer.compression.type=lz4
producer.acks=1
```

### Plugin Directory

Verify the plugin path is configured:

```bash
# Check connect-distributed.properties
grep plugin.path $KAFKA_HOME/config/connect-distributed.properties
```

Expected output:
```properties
plugin.path=/usr/local/share/kafka/plugins
```

!!! warning "Plugin Path Must Exist"
    The plugin directory must exist and have proper permissions:
    ```bash
    sudo mkdir -p /usr/local/share/kafka/plugins
    sudo chown -R $USER:$USER /usr/local/share/kafka/plugins
    ```

## Resource Requirements

### Minimum Resources

For development/testing:

| Resource | Requirement |
|----------|-------------|
| CPU | 1 core |
| Memory | 512 MB for connector |
| Disk | 100 MB for JAR files |
| Network | 10 Mbps |

### Production Resources

For production deployments:

| Resource | Requirement |
|----------|-------------|
| CPU | 2+ cores |
| Memory | 2 GB for Kafka Connect worker |
| Disk | 1 GB (for JARs + logs) |
| Network | 100+ Mbps |

### Memory Configuration

Configure heap size for Kafka Connect:

```bash
# In connect-distributed.sh or systemd service
export KAFKA_HEAP_OPTS="-Xms2G -Xmx2G"
```

## Security Configuration

### Secure JMX Access

For production, enable JMX authentication:

```bash
# Create password file
echo "admin changeit" > /etc/kafka/jmx.password
chmod 600 /etc/kafka/jmx.password

# Create access file
echo "admin readwrite" > /etc/kafka/jmx.access
chmod 644 /etc/kafka/jmx.access

# Configure Kafka Connect
export KAFKA_JMX_OPTS="-Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.authenticate=true \
  -Dcom.sun.management.jmxremote.password.file=/etc/kafka/jmx.password \
  -Dcom.sun.management.jmxremote.access.file=/etc/kafka/jmx.access \
  -Dcom.sun.management.jmxremote.ssl=true"
```

!!! danger "Never Run JMX Without Authentication in Production"
    Default JMX configuration with `authenticate=false` exposes your connector to unauthorized access.

### Protect Authentication Tokens

Do NOT store tokens in plaintext config files. Use Kafka Connect's ConfigProvider:

```json
{
  "websocket.auth.token": "${file:/etc/kafka/secrets.properties:websocket.token}",
  "config.providers": "file",
  "config.providers.file.class": "org.apache.kafka.common.config.provider.FileConfigProvider"
}
```

Create `/etc/kafka/secrets.properties` with restricted permissions:

```bash
echo "websocket.token=your-secret-token" > /etc/kafka/secrets.properties
chmod 600 /etc/kafka/secrets.properties
```

### SSL/TLS for WebSocket

For `wss://` endpoints, ensure Java trusts the server certificate:

```bash
# Add certificate to truststore
keytool -import -trustcacerts -alias myserver \
  -file server.crt \
  -keystore $JAVA_HOME/lib/security/cacerts \
  -storepass changeit
```

## Optional Tools

### Monitoring Tools

For production deployments, consider installing:

- **JMX Monitoring**: JConsole, VisualVM, or JMX Exporter
- **Prometheus**: For metrics collection
- **Grafana**: For dashboards
- **Loki/ELK**: For log aggregation

### Development Tools

For connector development:

- **Git**: Version control
- **Docker**: Containerized Kafka setup
- **curl/jq**: API testing and JSON parsing

## Verification Checklist

Before proceeding to installation, verify:

- [x] Java 11+ is installed and `java -version` works
- [x] Kafka 3.9.0+ is running
- [x] Kafka Connect REST API is accessible at `http://localhost:8083/`
- [x] Plugin directory exists and is writable
- [x] Maven 3.6+ is installed (for building from source)
- [x] Network access to WebSocket endpoints is available
- [x] Firewall/proxy allows WebSocket connections

## Troubleshooting Prerequisites

### Java Version Issues

**Problem:** Wrong Java version

```bash
# Check all Java installations
ls -la /usr/lib/jvm/

# Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### Kafka Not Running

**Problem:** Kafka Connect not accessible

```bash
# Check Kafka Connect logs
tail -f $KAFKA_HOME/logs/connect.log

# Restart Kafka Connect
$KAFKA_HOME/bin/connect-distributed.sh config/connect-distributed.properties
```

### Network Connectivity Issues

**Problem:** Cannot reach WebSocket endpoint

```bash
# Test DNS resolution
nslookup stream.binance.com

# Test port connectivity
telnet stream.binance.com 9443

# Test WebSocket handshake
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: test" \
  https://stream.binance.com:9443/ws
```

## Next Steps

Once all prerequisites are met:

1. [Installation](https://github.com/conduktor/kafka-connect-websocket/blob/main/README.md#installation) - Install the connector
2. [Quick Start](https://github.com/conduktor/kafka-connect-websocket/blob/main/README.md#usage-examples) - Deploy your first connector

---

**Need help?** Check our [FAQ](../faq.md) or [open an issue](https://github.com/conduktor/kafka-connect-websocket/issues).
