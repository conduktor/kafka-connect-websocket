package io.conduktor.connect.websocket;

import org.apache.kafka.common.config.Config;
import org.apache.kafka.common.config.ConfigValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WebSocketSourceConnector.validate() method.
 * Addresses SME review finding: "BLOCKER: No tests verify Connector.validate() behavior"
 */
class WebSocketSourceConnectorValidateTest {

    private WebSocketSourceConnector connector;

    @BeforeEach
    void setUp() {
        connector = new WebSocketSourceConnector();
    }

    @Test
    void testValidateWithValidConfiguration() {
        // Given: Valid configuration
        Map<String, String> props = createValidConfig();

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return no errors
        assertNotNull(result, "Validation result should not be null");
        List<ConfigValue> configValues = result.configValues();
        assertNotNull(configValues, "Config values should not be null");

        // Check that all required configs are present and valid
        boolean hasErrors = configValues.stream()
                .anyMatch(cv -> !cv.errorMessages().isEmpty());
        assertFalse(hasErrors, "Valid configuration should have no validation errors");
    }

    @Test
    void testValidateWithMissingRequiredUrl() {
        // Given: Configuration missing required websocket.url
        Map<String, String> props = createValidConfig();
        props.remove(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG);

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return validation error for missing URL
        assertNotNull(result, "Validation result should not be null");
        ConfigValue urlConfig = findConfigValue(result, WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG);
        assertNotNull(urlConfig, "URL config should be present in validation result");
        assertFalse(urlConfig.errorMessages().isEmpty(),
                "Missing required URL should produce validation error");
    }

    @Test
    void testValidateWithMissingRequiredTopic() {
        // Given: Configuration missing required kafka.topic
        Map<String, String> props = createValidConfig();
        props.remove(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG);

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return validation error for missing topic
        assertNotNull(result, "Validation result should not be null");
        ConfigValue topicConfig = findConfigValue(result, WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG);
        assertNotNull(topicConfig, "Topic config should be present in validation result");
        assertFalse(topicConfig.errorMessages().isEmpty(),
                "Missing required topic should produce validation error");
    }

    @Test
    void testValidateWithInvalidUrl() {
        // Given: Configuration with invalid WebSocket URL (wrong scheme)
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "http://invalid.com");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return validation error for invalid URL scheme
        assertNotNull(result, "Validation result should not be null");
        ConfigValue urlConfig = findConfigValue(result, WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG);
        assertNotNull(urlConfig, "URL config should be present in validation result");
        assertFalse(urlConfig.errorMessages().isEmpty(),
                "Invalid URL scheme should produce validation error");
        assertTrue(urlConfig.errorMessages().get(0).contains("ws://") || urlConfig.errorMessages().get(0).contains("wss://"),
                "Error message should mention required ws:// or wss:// scheme");
    }

    @Test
    void testValidateWithInvalidUrlSyntax() {
        // Given: Configuration with malformed URL
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://[invalid");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return validation error for malformed URL
        assertNotNull(result, "Validation result should not be null");
        ConfigValue urlConfig = findConfigValue(result, WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG);
        assertNotNull(urlConfig, "URL config should be present in validation result");
        assertFalse(urlConfig.errorMessages().isEmpty(),
                "Malformed URL should produce validation error");
    }

    @Test
    void testValidateWithEmptyUrl() {
        // Given: Configuration with empty URL
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should return validation error for empty URL
        assertNotNull(result, "Validation result should not be null");
        ConfigValue urlConfig = findConfigValue(result, WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG);
        assertNotNull(urlConfig, "URL config should be present in validation result");
        assertFalse(urlConfig.errorMessages().isEmpty(),
                "Empty URL should produce validation error");
    }

    @Test
    void testValidateWithOptionalSubscriptionMessage() {
        // Given: Valid configuration with optional subscription message
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG,
                "{\"action\":\"subscribe\",\"channel\":\"test\"}");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should be valid
        assertNotNull(result, "Validation result should not be null");
        ConfigValue subConfig = findConfigValue(result, WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG);
        assertNotNull(subConfig, "Subscription message config should be present in validation result");
        assertTrue(subConfig.errorMessages().isEmpty(),
                "Valid subscription message should not produce errors");
    }

    @Test
    void testValidateWithOptionalAuthToken() {
        // Given: Valid configuration with optional auth token
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "test-token-12345");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should be valid
        assertNotNull(result, "Validation result should not be null");
        ConfigValue authConfig = findConfigValue(result, WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG);
        assertNotNull(authConfig, "Auth token config should be present in validation result");
        assertTrue(authConfig.errorMessages().isEmpty(),
                "Valid auth token should not produce errors");
    }

    @Test
    void testValidateWithCustomHeaders() {
        // Given: Valid configuration with custom headers
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG,
                "User-Agent:TestClient,X-API-Key:12345");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should be valid
        assertNotNull(result, "Validation result should not be null");
        ConfigValue headersConfig = findConfigValue(result, WebSocketSourceConnectorConfig.HEADERS_CONFIG);
        assertNotNull(headersConfig, "Headers config should be present in validation result");
        assertTrue(headersConfig.errorMessages().isEmpty(),
                "Valid headers should not produce errors");
    }

    @Test
    void testValidateWithAllOptionalConfigs() {
        // Given: Valid configuration with all optional configs set
        Map<String, String> props = createValidConfig();
        props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG, "{\"action\":\"subscribe\"}");
        props.put(WebSocketSourceConnectorConfig.AUTH_TOKEN_CONFIG, "token");
        props.put(WebSocketSourceConnectorConfig.HEADERS_CONFIG, "User-Agent:Test");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "true");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_INTERVAL_MS_CONFIG, "5000");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_MAX_ATTEMPTS_CONFIG, "10");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "1000");
        props.put(WebSocketSourceConnectorConfig.CONNECTION_TIMEOUT_MS_CONFIG, "30000");

        // When: Validating configuration
        Config result = connector.validate(props);

        // Then: Should be valid
        assertNotNull(result, "Validation result should not be null");
        boolean hasErrors = result.configValues().stream()
                .anyMatch(cv -> !cv.errorMessages().isEmpty());
        assertFalse(hasErrors, "All valid configs should produce no validation errors");
    }

    /**
     * Helper to create a valid baseline configuration
     */
    private Map<String, String> createValidConfig() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "wss://example.com/ws");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        return props;
    }

    /**
     * Helper to find a specific ConfigValue by name
     */
    private ConfigValue findConfigValue(Config config, String name) {
        return config.configValues().stream()
                .filter(cv -> cv.name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
