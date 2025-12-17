package io.conduktor.connect.websocket;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import java.util.Map;

/**
 * Configuration for the WebSocket Source Connector.
 */
public class WebSocketSourceConnectorConfig extends AbstractConfig {

    // Configuration keys
    public static final String WEBSOCKET_URL_CONFIG = "websocket.url";
    private static final String WEBSOCKET_URL_DOC = "WebSocket endpoint URL (ws:// or wss://)";

    public static final String KAFKA_TOPIC_CONFIG = "kafka.topic";
    private static final String KAFKA_TOPIC_DOC = "The Kafka topic to write WebSocket messages to";

    public static final String SUBSCRIPTION_MESSAGE_CONFIG = "websocket.subscription.message";
    private static final String SUBSCRIPTION_MESSAGE_DOC = "Optional JSON message to send after connection (for subscribing to channels)";

    public static final String RECONNECT_ENABLED_CONFIG = "websocket.reconnect.enabled";
    private static final String RECONNECT_ENABLED_DOC = "Enable automatic reconnection on disconnect";

    public static final String RECONNECT_INTERVAL_MS_CONFIG = "websocket.reconnect.interval.ms";
    private static final String RECONNECT_INTERVAL_MS_DOC = "Interval between reconnection attempts in milliseconds";

    public static final String HEADERS_CONFIG = "websocket.headers";
    private static final String HEADERS_CONFIG_DOC = "Custom headers for WebSocket connection (format: key1:value1,key2:value2)";

    public static final String AUTH_TOKEN_CONFIG = "websocket.auth.token";
    private static final String AUTH_TOKEN_DOC = "Optional authentication token to include in headers";

    public static final String MESSAGE_QUEUE_SIZE_CONFIG = "websocket.message.queue.size";
    private static final String MESSAGE_QUEUE_SIZE_DOC = "Maximum size of the message buffer queue";

    public static final String CONNECTION_TIMEOUT_MS_CONFIG = "websocket.connection.timeout.ms";
    private static final String CONNECTION_TIMEOUT_MS_DOC = "Connection timeout in milliseconds";

    public static final ConfigDef CONFIG_DEF = createConfigDef();

    private static ConfigDef createConfigDef() {
        return new ConfigDef()
                .define(
                        WEBSOCKET_URL_CONFIG,
                        Type.STRING,
                        ConfigDef.NO_DEFAULT_VALUE,
                        Importance.HIGH,
                        WEBSOCKET_URL_DOC
                )
                .define(
                        KAFKA_TOPIC_CONFIG,
                        Type.STRING,
                        ConfigDef.NO_DEFAULT_VALUE,
                        Importance.HIGH,
                        KAFKA_TOPIC_DOC
                )
                .define(
                        SUBSCRIPTION_MESSAGE_CONFIG,
                        Type.STRING,
                        null,
                        Importance.MEDIUM,
                        SUBSCRIPTION_MESSAGE_DOC
                )
                .define(
                        RECONNECT_ENABLED_CONFIG,
                        Type.BOOLEAN,
                        true,
                        Importance.MEDIUM,
                        RECONNECT_ENABLED_DOC
                )
                .define(
                        RECONNECT_INTERVAL_MS_CONFIG,
                        Type.LONG,
                        5000L,
                        Importance.MEDIUM,
                        RECONNECT_INTERVAL_MS_DOC
                )
                .define(
                        HEADERS_CONFIG,
                        Type.STRING,
                        null,
                        Importance.LOW,
                        HEADERS_CONFIG_DOC
                )
                .define(
                        AUTH_TOKEN_CONFIG,
                        Type.STRING,
                        null,
                        Importance.MEDIUM,
                        AUTH_TOKEN_DOC
                )
                .define(
                        MESSAGE_QUEUE_SIZE_CONFIG,
                        Type.INT,
                        10000,
                        Importance.LOW,
                        MESSAGE_QUEUE_SIZE_DOC
                )
                .define(
                        CONNECTION_TIMEOUT_MS_CONFIG,
                        Type.LONG,
                        30000L,
                        Importance.LOW,
                        CONNECTION_TIMEOUT_MS_DOC
                );
    }

    public WebSocketSourceConnectorConfig(Map<?, ?> originals) {
        super(CONFIG_DEF, originals);
    }

    public String getWebSocketUrl() {
        return getString(WEBSOCKET_URL_CONFIG);
    }

    public String getKafkaTopic() {
        return getString(KAFKA_TOPIC_CONFIG);
    }

    public String getSubscriptionMessage() {
        return getString(SUBSCRIPTION_MESSAGE_CONFIG);
    }

    public boolean isReconnectEnabled() {
        return getBoolean(RECONNECT_ENABLED_CONFIG);
    }

    public long getReconnectIntervalMs() {
        return getLong(RECONNECT_INTERVAL_MS_CONFIG);
    }

    public String getHeaders() {
        return getString(HEADERS_CONFIG);
    }

    public String getAuthToken() {
        return getString(AUTH_TOKEN_CONFIG);
    }

    public int getMessageQueueSize() {
        return getInt(MESSAGE_QUEUE_SIZE_CONFIG);
    }

    public long getConnectionTimeoutMs() {
        return getLong(CONNECTION_TIMEOUT_MS_CONFIG);
    }
}
