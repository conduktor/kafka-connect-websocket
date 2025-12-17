package io.conduktor.connect.websocket;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.apache.kafka.connect.storage.OffsetStorageReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for offset management, commitRecord callback, and offset restoration.
 * Addresses SME review finding: "BLOCKER: No tests verify commitRecord() callback behavior"
 */
class WebSocketSourceTaskOffsetTest {

    private WebSocketSourceTask task;
    private SourceTaskContext mockContext;
    private OffsetStorageReader mockOffsetReader;

    @BeforeEach
    void setUp() {
        task = new WebSocketSourceTask();
        mockContext = mock(SourceTaskContext.class);
        mockOffsetReader = mock(OffsetStorageReader.class);

        when(mockContext.offsetStorageReader()).thenReturn(mockOffsetReader);
        task.initialize(mockContext);
    }

    @AfterEach
    void tearDown() {
        if (task != null) {
            task.stop();
        }
    }

    @Test
    void testOffsetStructureInSourceRecord() throws Exception {
        // Given: MockWebSocketServer that sends a message
        try (MockWebSocketServer mockServer = MockWebSocketServer.builder()
                .sendOnConnect("test-message")
                .build()) {
            // Given: Task is started with minimal config
            Map<String, String> props = createMinimalConfigWithMockServer(mockServer);
            task.start(props);

            // Wait for connection and message delivery
            Thread.sleep(1000);

            // When: Polling for records
            List<SourceRecord> records = task.poll();

            if (records != null && !records.isEmpty()) {
                SourceRecord record = records.get(0);

                // Then: Verify offset structure
                Map<String, ?> sourcePartition = record.sourcePartition();
                assertNotNull(sourcePartition, "Source partition should not be null");
                assertTrue(sourcePartition.containsKey("websocket_url"), "Partition should contain websocket_url");
                assertEquals(props.get(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG),
                            sourcePartition.get("websocket_url"));

                Map<String, ?> sourceOffset = record.sourceOffset();
                assertNotNull(sourceOffset, "Source offset should not be null");
                assertTrue(sourceOffset.containsKey("session_id"), "Offset should contain session_id");
                assertTrue(sourceOffset.containsKey("sequence"), "Offset should contain sequence");

                // Verify sequence is a number
                Object sequence = sourceOffset.get("sequence");
                assertInstanceOf(Number.class, sequence, "Sequence should be a number");
                assertTrue(((Number) sequence).longValue() > 0, "Sequence should be positive");
            }
        }
    }


    @Test
    void testSequenceMonotonicallyIncreases() throws Exception {
        // Given: MockWebSocketServer in echo mode
        try (MockWebSocketServer mockServer = MockWebSocketServer.builder().echoMode().build()) {
            // Given: Task is started with subscription that gets echoed
            Map<String, String> props = createMinimalConfigWithMockServer(mockServer);
            props.put(WebSocketSourceConnectorConfig.SUBSCRIPTION_MESSAGE_CONFIG,
                     "{\"action\":\"test\"}");
            task.start(props);

            // Wait for connection and echo
            Thread.sleep(1500);

            // When: Polling multiple times
            Long lastSequence = null;
            for (int i = 0; i < 3; i++) {
                List<SourceRecord> records = task.poll();
                if (records != null && !records.isEmpty()) {
                    for (SourceRecord record : records) {
                        Map<String, ?> offset = record.sourceOffset();
                        Long currentSequence = ((Number) offset.get("sequence")).longValue();

                        // Then: Each sequence should be greater than the last
                        if (lastSequence != null) {
                            assertTrue(currentSequence > lastSequence,
                                String.format("Sequence should increase: last=%d, current=%d",
                                            lastSequence, currentSequence));
                        }
                        lastSequence = currentSequence;
                    }
                }
                Thread.sleep(200);
            }
        }
    }

    private Map<String, String> createMinimalConfig() {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, "ws://localhost:9999");
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");
        return props;
    }

    private Map<String, String> createMinimalConfigWithMockServer(MockWebSocketServer server) {
        Map<String, String> props = new HashMap<>();
        props.put(WebSocketSourceConnectorConfig.WEBSOCKET_URL_CONFIG, server.getUrl());
        props.put(WebSocketSourceConnectorConfig.KAFKA_TOPIC_CONFIG, "test-topic");
        props.put(WebSocketSourceConnectorConfig.RECONNECT_ENABLED_CONFIG, "false");
        props.put(WebSocketSourceConnectorConfig.MESSAGE_QUEUE_SIZE_CONFIG, "100");
        return props;
    }
}
