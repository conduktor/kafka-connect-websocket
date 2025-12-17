package io.conduktor.connect.websocket;

import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketSourceConnectorConfigTest {

    @Test
    void testValidConfiguration() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);

        assertEquals("wss://example.com/ws", config.getWebSocketUrl());
        assertEquals("test-topic", config.getKafkaTopic());
        assertTrue(config.isReconnectEnabled()); // default
        assertEquals(5000L, config.getReconnectIntervalMs()); // default
        assertNull(config.getSubscriptionMessage()); // not set
        assertEquals(10000, config.getMessageQueueSize()); // default
    }

    @Test
    void testMissingRequiredUrl() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testMissingRequiredTopic() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");

        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testCustomConfiguration() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"subscribe\"}");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "10000");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "User-Agent:test,X-Custom:value");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "mytoken123");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "5000");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);

        assertEquals("wss://example.com/ws", config.getWebSocketUrl());
        assertEquals("test-topic", config.getKafkaTopic());
        assertEquals("{\"action\":\"subscribe\"}", config.getSubscriptionMessage());
        assertFalse(config.isReconnectEnabled());
        assertEquals(10000L, config.getReconnectIntervalMs());
        assertEquals("User-Agent:test,X-Custom:value", config.getHeaders());
        assertEquals("mytoken123", config.getAuthToken());
        assertEquals(5000, config.getMessageQueueSize());
    }

    @Test
    void testConfigDefNotNull() {
        assertNotNull(WebSocketSourceConnectorConfig.CONFIG_DEF);
        assertTrue(WebSocketSourceConnectorConfig.CONFIG_DEF.configKeys().containsKey(
                WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG));
        assertTrue(WebSocketSourceConnectorConfig.CONFIG_DEF.configKeys().containsKey(
                WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG));
    }
}
