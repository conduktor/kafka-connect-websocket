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

    // ========================================================================================
    // CONFIGURATION VALIDATION TESTS
    // ========================================================================================

    @Test
    void testInvalidUrlFormatHttp() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "http://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Config now validates URL scheme - http:// should be rejected
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testInvalidUrlFormatHttps() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "https://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Config now validates URL scheme - https:// should be rejected
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testInvalidUrlFormatNoProtocol() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Config now validates URL scheme - missing protocol should be rejected
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testInvalidUrlEmpty() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Empty URL should throw ConfigException
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testInvalidUrlWhitespaceOnly() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "   ");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Whitespace-only URL should now be rejected by validation
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testValidWsUrl() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("ws://example.com/ws", config.getWebSocketUrl());
    }

    @Test
    void testValidWssUrl() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("wss://example.com/ws", config.getWebSocketUrl());
    }

    // ========================================================================================
    // QUEUE SIZE VALIDATION TESTS
    // ========================================================================================

    @Test
    void testNegativeQueueSize() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "-100");

        // ConfigDef Type.INT will throw for invalid integer values
        // Negative values might be accepted by ConfigDef but cause issues at runtime
        assertDoesNotThrow(() -> new WebSocketSourceConnectorConfig(props));
    }

    @Test
    void testZeroQueueSize() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "0");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(0, config.getMessageQueueSize());
    }

    @Test
    void testVeryLargeQueueSize() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "1000000");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(1000000, config.getMessageQueueSize());
    }

    @Test
    void testInvalidQueueSizeNotANumber() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "not-a-number");

        // ConfigDef Type.INT validation should throw
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    // ========================================================================================
    // HEADER FORMAT VALIDATION TESTS
    // ========================================================================================

    @Test
    void testValidHeaderFormat() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "User-Agent:TestClient,X-Custom:value");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("User-Agent:TestClient,X-Custom:value", config.getHeaders());
    }

    @Test
    void testInvalidHeaderFormatNoColon() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "InvalidHeader");

        // Config accepts it - parsing happens in WebSocketSourceTask
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("InvalidHeader", config.getHeaders());
    }

    @Test
    void testInvalidHeaderFormatMultipleColons() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "Key:Value:Extra");

        // Config accepts it - the split with limit 2 will handle this
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("Key:Value:Extra", config.getHeaders());
    }

    @Test
    void testHeaderWithSpaces() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, " User-Agent : Test Client , X-Custom : value ");

        // Config trims the header string
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("User-Agent : Test Client , X-Custom : value", config.getHeaders());
    }

    @Test
    void testEmptyHeaders() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("", config.getHeaders());
    }

    // ========================================================================================
    // HEADER INJECTION TESTS
    // ========================================================================================

    @Test
    void testHeaderInjectionAttemptNewline() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "X-Header:value\nInjected-Header:malicious");

        // Config accepts it - OkHttp will validate header values
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertNotNull(config.getHeaders());
    }

    @Test
    void testHeaderInjectionAttemptCarriageReturn() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "X-Header:value\r\nInjected-Header:malicious");

        // Config accepts it - OkHttp will validate
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertNotNull(config.getHeaders());
    }

    @Test
    void testHeaderWithSpecialCharacters() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "X-Special:@#$%^&*()");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("X-Special:@#$%^&*()", config.getHeaders());
    }

    // ========================================================================================
    // RECONNECT INTERVAL VALIDATION TESTS
    // ========================================================================================

    @Test
    void testNegativeReconnectInterval() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "-1000");

        // Negative intervals are accepted but may cause issues
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(-1000L, config.getReconnectIntervalMs());
    }

    @Test
    void testZeroReconnectInterval() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "0");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(0L, config.getReconnectIntervalMs());
    }

    @Test
    void testVeryLargeReconnectInterval() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "3600000"); // 1 hour

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(3600000L, config.getReconnectIntervalMs());
    }

    @Test
    void testInvalidReconnectIntervalNotANumber() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "invalid");

        // ConfigDef Type.LONG validation should throw
        assertThrows(ConfigException.class, () -> new WebSocketSourceConnectorConfig(props));
    }

    // ========================================================================================
    // AUTH TOKEN VALIDATION TESTS
    // ========================================================================================

    @Test
    void testValidAuthToken() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "valid-token-12345");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("valid-token-12345", config.getAuthToken());
    }

    @Test
    void testEmptyAuthToken() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("", config.getAuthToken());
    }

    @Test
    void testAuthTokenWithSpecialCharacters() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "token!@#$%^&*()_+-=[]{}|;':\",./<>?");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertNotNull(config.getAuthToken());
    }

    @Test
    void testAuthTokenWithWhitespace() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "  token-with-spaces  ");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        // Config trims the auth token
        assertEquals("token-with-spaces", config.getAuthToken());
    }

    @Test
    void testVeryLongAuthToken() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");

        // Create a very long token
        StringBuilder longToken = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longToken.append("a");
        }
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, longToken.toString());

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(1000, config.getAuthToken().length());
    }

    // ========================================================================================
    // CONNECTION TIMEOUT VALIDATION TESTS
    // ========================================================================================

    @Test
    void testNegativeConnectionTimeout() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "-5000");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(-5000L, config.getConnectionTimeoutMs());
    }

    @Test
    void testZeroConnectionTimeout() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "0");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(0L, config.getConnectionTimeoutMs());
    }

    @Test
    void testVeryLargeConnectionTimeout() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "300000"); // 5 minutes

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals(300000L, config.getConnectionTimeoutMs());
    }

    // ========================================================================================
    // TOPIC VALIDATION TESTS
    // ========================================================================================

    @Test
    void testEmptyTopicName() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "");

        // Empty topic - ConfigDef may or may not throw depending on validation rules
        // Current implementation appears to allow empty strings, so we test actual behavior
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("", config.getKafkaTopic());
    }

    @Test
    void testTopicWithSpecialCharacters() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "topic-with-dashes_and_underscores.and.dots");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("topic-with-dashes_and_underscores.and.dots", config.getKafkaTopic());
    }

    @Test
    void testTopicWithWhitespace() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "  topic-with-spaces  ");

        // Config trims the topic name
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("topic-with-spaces", config.getKafkaTopic());
    }

    // ========================================================================================
    // SUBSCRIPTION MESSAGE VALIDATION TESTS
    // ========================================================================================

    @Test
    void testValidJsonSubscriptionMessage() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"subscribe\",\"channel\":\"test\"}");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("{\"action\":\"subscribe\",\"channel\":\"test\"}", config.getSubscriptionMessage());
    }

    @Test
    void testInvalidJsonSubscriptionMessage() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{invalid json");

        // Config accepts any string - validation happens at runtime
        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("{invalid json", config.getSubscriptionMessage());
    }

    @Test
    void testPlainTextSubscriptionMessage() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "SUBSCRIBE:test-channel");

        WebSocketSourceConnectorConfig config = new WebSocketSourceConnectorConfig(props);
        assertEquals("SUBSCRIBE:test-channel", config.getSubscriptionMessage());
    }
}
