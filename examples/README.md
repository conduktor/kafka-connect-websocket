# Docker Compose Example

Quick start with Docker Compose to run Kafka, Kafka Connect, and the WebSocket connector.

## Prerequisites

- Docker and Docker Compose installed
- Built connector JAR in `../target/`

## Build the Connector First

```bash
cd ..
mvn clean package
cd examples
```

## Start the Stack

```bash
docker-compose up -d
```

This starts:
- **Kafka** (KRaft mode) on port 9092
- **Kafka Connect** on port 8083
- **Conduktor Console** on port 8080 (web UI for viewing topics)

## Deploy a Connector

You can deploy connectors via Conduktor Console at http://localhost:8080 or via the REST API.

Wait for Kafka Connect to be ready:

```bash
# Check Connect is running
curl http://localhost:8083/

# Verify plugin is loaded
curl http://localhost:8083/connector-plugins | jq '.[] | select(.class | contains("WebSocket"))'
```

Deploy the Binance connector:

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

## View Messages

Option 1: Conduktor Console at http://localhost:8080

Option 2: Command line:

```bash
docker-compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic binance-btcusdt-trades \
  --from-beginning
```

## Check Connector Status

```bash
curl http://localhost:8083/connectors/binance-btcusdt/status | jq .
```

## Stop

```bash
docker-compose down -v
```

## Troubleshooting

### Connector not appearing in plugin list

Ensure the JAR is built:

```bash
ls ../target/kafka-connect-websocket-*-jar-with-dependencies.jar
```

If missing, rebuild:

```bash
cd .. && mvn clean package && cd examples
docker-compose restart kafka-connect
```

### Connection issues

Check Kafka Connect logs:

```bash
docker-compose logs kafka-connect
```
