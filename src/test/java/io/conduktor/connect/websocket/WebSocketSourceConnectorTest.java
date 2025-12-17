package io.conduktor.connect.websocket;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSourceConnectorTest {

    @Test
    void testConnectorStartStop() {
        WebSocketSourceConnector connector = new WebSocketSourceConnector();

        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Start should not throw
        assertDoesNotThrow(() -> connector.start(props));

        // Stop should not throw
        assertDoesNotThrow(connector::stop);
    }

    @Test
    void testTaskClass() {
        WebSocketSourceConnector connector = new WebSocketSourceConnector();
        assertEquals(WebSocketSourceTask.class, connector.taskClass());
    }

    @Test
    void testTaskConfigs() {
        WebSocketSourceConnector connector = new WebSocketSourceConnector();

        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        connector.start(props);

        // Should always return exactly 1 task config
        List<Map<String, String>> taskConfigs = connector.taskConfigs(1);
        assertEquals(1, taskConfigs.size());
        assertEquals(props, taskConfigs.get(0));

        // Even if we request more tasks, should still return 1
        taskConfigs = connector.taskConfigs(5);
        assertEquals(1, taskConfigs.size());
    }

    @Test
    void testVersion() {
        WebSocketSourceConnector connector = new WebSocketSourceConnector();
        assertNotNull(connector.version());
        assertFalse(connector.version().isEmpty());
    }

    @Test
    void testConfig() {
        WebSocketSourceConnector connector = new WebSocketSourceConnector();
        assertNotNull(connector.config());
        assertEquals(WebSocketSourceConnectorConfig.CONFIG_DEF, connector.config());
    }
}
