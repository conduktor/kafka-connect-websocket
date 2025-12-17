package io.conduktor.connect.websocket;

import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WebSocket Source Connector for Kafka Connect.
 * Streams data from WebSocket endpoints into Kafka topics.
 */
public class WebSocketSourceConnector extends SourceConnector {
    private static final Logger log = LoggerFactory.getLogger(WebSocketSourceConnector.class);

    private Map<String, String> configProperties;

    @Override
    public String version() {
        return VersionUtil.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
        log.info("Starting WebSocket Source Connector");
        this.configProperties = props;

        // Validate configuration
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        log.info("Connector configured for WebSocket URL: {}", config.getWebSocketUrl());
        log.info("Target Kafka topic: {}", config.getKafkaTopic());
    }

    @Override
    public Class<? extends Task> taskClass() {
        return WebSocketSourceTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        log.info("Creating task configurations for {} tasks", maxTasks);

        // WebSocket connections are single-threaded, so we only create one task
        List<Map<String, String>> taskConfigs = new ArrayList<>(1);
        taskConfigs.add(configProperties);

        return taskConfigs;
    }

    @Override
    public void stop() {
        log.info("Stopping WebSocket Source Connector");
    }

    @Override
    public ConfigDef config() {
        return WebSocketSourceConnectorConfig.CONFIG_DEF;
    }
}
