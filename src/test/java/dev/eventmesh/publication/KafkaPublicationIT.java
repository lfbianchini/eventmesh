package dev.eventmesh.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@SpringBootTest(
    properties = {
      "eventmesh.outbox.scheduling-enabled=false",
      "eventmesh.outbox.send-timeout=1s",
      "spring.kafka.producer.properties.max.block.ms=1000",
      "spring.kafka.producer.properties.request.timeout.ms=1000",
      "spring.kafka.producer.properties.delivery.timeout.ms=3000",
      "eventmesh.webhooks.kafka-listener-enabled=false",
      "eventmesh.webhooks.scheduling-enabled=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class KafkaPublicationIT {

  private static final String LOCAL_API_KEY = "em_dev_local_key_change_me";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("eventmesh")
          .withUsername("eventmesh")
          .withPassword("eventmesh")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

  @Container
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

  @DynamicPropertySource
  static void containerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired OutboxRelay relay;

  @BeforeEach
  void resetDatabase() {
    jdbcTemplate.update("DELETE FROM outbox_messages");
    jdbcTemplate.update("DELETE FROM events");
  }

  @Test
  void publishesTheVersionedEnvelopeToARealKafkaBroker() throws Exception {
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "eventmesh-publication-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class))) {
      consumer.subscribe(List.of(PublicationConfiguration.ACCEPTED_EVENTS_TOPIC));

      var eventId = createEvent();
      var result = relay.publishAvailable();
      var records = consumer.poll(Duration.ofSeconds(10));

      assertThat(result).isEqualTo(new PublicationBatchResult(1, 1, 0));
      var record =
          StreamSupport.stream(
                  records.records(PublicationConfiguration.ACCEPTED_EVENTS_TOPIC).spliterator(),
                  false)
              .filter(
                  candidate -> {
                    try {
                      return objectMapper
                          .readTree(candidate.value())
                          .at("/event/id")
                          .asText()
                          .equals(eventId);
                    } catch (Exception ignored) {
                      return false;
                    }
                  })
              .findFirst()
              .orElseThrow();
      var envelope = objectMapper.readTree(record.value());
      var persistedMessageId =
          jdbcTemplate.queryForObject(
              "SELECT id FROM outbox_messages WHERE event_id = ?",
              UUID.class,
              UUID.fromString(eventId));

      assertThat(record.key()).isEqualTo(eventId);
      assertThat(envelope.get("specVersion").asText()).isEqualTo("1.0");
      assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(envelope.get("messageType").asText()).isEqualTo("event.accepted");
      assertThat(envelope.get("messageId").asText()).isEqualTo(persistedMessageId.toString());
      assertThat(envelope.at("/event/id").asText()).isEqualTo(eventId);
      assertThat(envelope.at("/event/data/amount").asInt()).isEqualTo(4200);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT published_at IS NOT NULL FROM outbox_messages WHERE id = ?",
                  Boolean.class,
                  persistedMessageId))
          .isTrue();
    }
  }

  @Test
  void retainsTheOutboxMessageWhileTheBrokerIsPausedAndPublishesAfterRecovery()
      throws Exception {
    var dockerClient = DockerClientFactory.instance().client();
    dockerClient.pauseContainerCmd(KAFKA.getContainerId()).exec();
    String eventId;
    try {
      eventId = createEvent("kafka-outage-recovery-1");
      var failed = relay.publishAvailable();

      assertThat(failed.failed()).isEqualTo(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  """
                  SELECT published_at IS NULL
                  FROM outbox_messages
                  WHERE event_id = ?
                  """,
                  Boolean.class,
                  UUID.fromString(eventId)))
          .isTrue();
    } finally {
      dockerClient.unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    PublicationBatchResult recovered = new PublicationBatchResult(0, 0, 0);
    var deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    while (recovered.published() == 0 && System.nanoTime() < deadline) {
      jdbcTemplate.update(
          """
          UPDATE outbox_messages
          SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
          WHERE event_id = ?
          """,
          UUID.fromString(eventId));
      recovered = relay.publishAvailable();
      if (recovered.published() == 0) {
        Thread.sleep(250);
      }
    }

    assertThat(recovered.published()).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT published_at IS NOT NULL
                FROM outbox_messages
                WHERE event_id = ?
                """,
                Boolean.class,
                UUID.fromString(eventId)))
        .isTrue();
  }

  private String createEvent() throws Exception {
    return createEvent("kafka-publication-1");
  }

  private String createEvent(String idempotencyKey) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/events")
                    .header("X-EventMesh-Key", LOCAL_API_KEY)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "type": "order.created",
                          "source": "test.checkout",
                          "subject": "order-123",
                          "data": {"amount": 4200, "currency": "USD"}
                        }
                        """))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
