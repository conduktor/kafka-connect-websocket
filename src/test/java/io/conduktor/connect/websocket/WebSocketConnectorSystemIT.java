package io.conduktor.connect.websocket;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * System integration test that verifies the complete flow:
 * WebSocket -> Kafka Connect -> Kafka Topic
 *
 * Uses Testcontainers to spin up:
 * - Kafka (with KRaft)
 * - Kafka Connect with the WebSocket connector
 * - MockWebSocketServer on the host for sending test messages
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebSocketConnectorSystemIT {

    private static final String TOPIC = "websocket-system-test";
    private static final String CONNECTOR_NAME = "websocket-system-test-connector";

    private static Network network;
    private static MockWebSocketServer mockWebSocketServer;
    private static String hostWebSocketUrl;

    @Container
    private static final KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withNetwork(Network.newNetwork())
            .withNetworkAliases("kafka");

    private static GenericContainer<?> kafkaConnect;
    private static HttpClient httpClient;

    @BeforeAll
    static void setUpAll() throws Exception {
        network = kafka.getNetwork();
        httpClient = HttpClient.newHttpClient();

        // Start mock WebSocket server on host - use autoAccept mode for reconnections
        mockWebSocketServer = MockWebSocketServer.builder().autoAccept().build();

        // Expose the host port to Docker containers
        int wsPort = mockWebSocketServer.getPort();
        org.testcontainers.Testcontainers.exposeHostPorts(wsPort);

        // Use host.testcontainers.internal to access host from container
        hostWebSocketUrl = "ws://host.testcontainers.internal:" + wsPort;

        // Find the built JAR
        String jarPath = findConnectorJar();

        // Start Kafka Connect container with our connector
        kafkaConnect = new GenericContainer<>(DockerImageName.parse("confluentinc/cp-kafka-connect:7.5.0"))
                .withNetwork(network)
                .withNetworkAliases("kafka-connect")
                .withExposedPorts(8083)
                .withEnv("CONNECT_BOOTSTRAP_SERVERS", "kafka:9092")
                .withEnv("CONNECT_REST_PORT", "8083")
                .withEnv("CONNECT_GROUP_ID", "websocket-test-group")
                .withEnv("CONNECT_CONFIG_STORAGE_TOPIC", "connect-configs")
                .withEnv("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("CONNECT_OFFSET_STORAGE_TOPIC", "connect-offsets")
                .withEnv("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("CONNECT_STATUS_STORAGE_TOPIC", "connect-status")
                .withEnv("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1")
                .withEnv("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
                .withEnv("CONNECT_VALUE_CONVERTER", "org.apache.kafka.connect.storage.StringConverter")
                .withEnv("CONNECT_REST_ADVERTISED_HOST_NAME", "kafka-connect")
                .withEnv("CONNECT_PLUGIN_PATH", "/usr/share/java,/usr/share/confluent-hub-components,/connect-plugins")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(jarPath),
                        "/connect-plugins/kafka-connect-websocket/kafka-connect-websocket.jar")
                .withAccessToHost(true)
                .waitingFor(Wait.forHttp("/connectors").forPort(8083).withStartupTimeout(Duration.ofMinutes(2)))
                .dependsOn(kafka);

        kafkaConnect.start();

        // Wait for Connect to be fully ready
        waitForConnectReady();
    }

    @AfterAll
    static void tearDownAll() throws Exception {
        if (kafkaConnect != null) {
            // Delete connector before shutting down
            try {
                deleteConnector();
            } catch (Exception ignored) {
            }
            kafkaConnect.stop();
        }
        if (mockWebSocketServer != null) {
            mockWebSocketServer.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Kafka Connect should load the WebSocket connector plugin")
    void testConnectorPluginLoaded() throws Exception {
        String response = httpGet("/connector-plugins");

        assertTrue(response.contains("WebSocketSourceConnector"),
                "WebSocket connector plugin should be loaded. Available plugins: " + response);
    }

    @Test
    @Order(2)
    @DisplayName("Should deploy WebSocket connector successfully")
    void testDeployConnector() throws Exception {
        String config = String.format(
                "{" +
                "\"name\": \"%s\"," +
                "\"config\": {" +
                    "\"connector.class\": \"io.conduktor.connect.websocket.WebSocketSourceConnector\"," +
                    "\"tasks.max\": \"1\"," +
                    "\"websocket.url\": \"%s\"," +
                    "\"kafka.topic\": \"%s\"," +
                    "\"websocket.reconnect.enabled\": \"true\"," +
                    "\"websocket.reconnect.interval.ms\": \"1000\"," +
                    "\"websocket.message.queue.size\": \"1000\"" +
                "}" +
                "}", CONNECTOR_NAME, hostWebSocketUrl, TOPIC);

        String response = httpPost("/connectors", config);

        assertTrue(response.contains(CONNECTOR_NAME),
                "Connector should be created. Response: " + response);

        // Wait for connector to start
        waitForConnectorRunning();
    }

    @Test
    @Order(3)
    @DisplayName("Should receive messages from WebSocket in Kafka topic")
    void testMessagesFlowToKafka() throws Exception {
        // Wait for WebSocket connection from Kafka Connect
        assertTrue(waitForCondition(() -> mockWebSocketServer.hasActiveConnection(), 30, TimeUnit.SECONDS),
                "Kafka Connect should establish WebSocket connection");

        // Send test messages through the mock WebSocket server
        List<String> testMessages = List.of(
                "{\"event\":\"test1\",\"value\":100}",
                "{\"event\":\"test2\",\"value\":200}",
                "{\"event\":\"test3\",\"value\":300}"
        );

        for (String msg : testMessages) {
            mockWebSocketServer.sendMessage(msg);
            Thread.sleep(100); // Small delay between messages
        }

        // Consume messages from Kafka and verify
        List<String> receivedMessages = consumeMessages(TOPIC, testMessages.size(), Duration.ofSeconds(30));

        assertEquals(testMessages.size(), receivedMessages.size(),
                "Should receive all test messages. Received: " + receivedMessages);

        for (String expected : testMessages) {
            assertTrue(receivedMessages.stream().anyMatch(m -> m.contains(expected.substring(10, 20))),
                    "Should receive message containing: " + expected);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Connector should handle high message throughput")
    void testHighThroughput() throws Exception {
        // Ensure WebSocket connection is active
        assertTrue(waitForCondition(() -> mockWebSocketServer.hasActiveConnection(), 10, TimeUnit.SECONDS),
                "WebSocket connection should be active");

        int messageCount = 100;
        String testId = UUID.randomUUID().toString().substring(0, 8);

        // Send burst of messages with unique test ID
        for (int i = 0; i < messageCount; i++) {
            String msg = String.format("{\"test\":\"%s\",\"seq\":%d,\"ts\":%d}", testId, i, System.currentTimeMillis());
            mockWebSocketServer.sendMessage(msg);
        }

        // Consume and verify - filter by test ID to count only this test's messages
        List<String> receivedMessages = consumeMessagesWithFilter(TOPIC, testId, messageCount, Duration.ofSeconds(30));

        // Should receive all messages (no shutdown/crash during test)
        assertEquals(messageCount, receivedMessages.size(),
                "Should receive all messages. Sent: " + messageCount + ", Received: " + receivedMessages.size());
    }

    @Test
    @Order(5)
    @DisplayName("Connector status should show RUNNING")
    void testConnectorStatus() throws Exception {
        String response = httpGet("/connectors/" + CONNECTOR_NAME + "/status");

        assertTrue(response.contains("\"state\":\"RUNNING\""),
                "Connector should be in RUNNING state. Status: " + response);
        assertTrue(response.contains("\"tasks\""),
                "Connector should have tasks. Status: " + response);
    }

    // --- Helper methods ---

    private static String findConnectorJar() {
        java.io.File targetDir = new java.io.File("target");
        java.io.File[] jars = targetDir.listFiles((dir, name) ->
                name.startsWith("kafka-connect-websocket") && name.endsWith("-jar-with-dependencies.jar"));

        if (jars == null || jars.length == 0) {
            throw new IllegalStateException(
                    "Connector JAR not found in target/. Run 'mvn package' first.");
        }
        return jars[0].getAbsolutePath();
    }

    private static void waitForConnectReady() throws Exception {
        int maxAttempts = 60;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                String response = httpGet("/");
                if (response.contains("version")) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("Kafka Connect did not become ready in time");
    }

    private static void waitForConnectorRunning() throws Exception {
        int maxAttempts = 30;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                String response = httpGet("/connectors/" + CONNECTOR_NAME + "/status");
                if (response.contains("\"state\":\"RUNNING\"")) {
                    return;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(1000);
        }

        // Get final status for error message
        String finalStatus = httpGet("/connectors/" + CONNECTOR_NAME + "/status");
        throw new IllegalStateException("Connector did not reach RUNNING state. Status: " + finalStatus);
    }

    private static void deleteConnector() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getConnectUrl() + "/connectors/" + CONNECTOR_NAME))
                .DELETE()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String httpGet(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getConnectUrl() + path))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String httpPost(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getConnectUrl() + path))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private static String getConnectUrl() {
        return "http://" + kafkaConnect.getHost() + ":" + kafkaConnect.getMappedPort(8083);
    }

    private List<String> consumeMessages(String topic, int expectedCount, Duration timeout) {
        return consumeMessagesWithFilter(topic, null, expectedCount, timeout);
    }

    private List<String> consumeMessagesWithFilter(String topic, String filter, int expectedCount, Duration timeout) {
        List<String> messages = new CopyOnWriteArrayList<>();

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "system-test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (messages.size() < expectedCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    String value = record.value();
                    if (filter == null || value.contains(filter)) {
                        messages.add(value);
                    }
                }
            }
        }

        return messages;
    }

    private static boolean waitForCondition(java.util.function.BooleanSupplier condition, long timeout, TimeUnit unit) {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
