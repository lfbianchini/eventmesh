package dev.eventmesh.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    properties = {
      "eventmesh.outbox.scheduling-enabled=false",
      "eventmesh.kafka.create-topics=false",
      "eventmesh.webhooks.kafka-listener-enabled=false",
      "eventmesh.webhooks.scheduling-enabled=false",
      "eventmesh.observability.scheduling-enabled=false"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OutboxRecoveryIT {

  private static final String LOCAL_API_KEY = "em_dev_local_key_change_me";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("eventmesh")
          .withUsername("eventmesh")
          .withPassword("eventmesh")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired OutboxRelay relay;
  @Autowired RecordingPublisher publisher;

  @BeforeEach
  void reset() {
    jdbcTemplate.update("DELETE FROM outbox_messages");
    jdbcTemplate.update("DELETE FROM events");
    publisher.reset();
  }

  @Test
  void retainsAndRecoversTheSameLogicalMessageAfterPublisherFailure() throws Exception {
    var eventId = createEvent("outbox-recovery-1");
    var messageId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM outbox_messages WHERE event_id = ?",
            UUID.class,
            UUID.fromString(eventId));

    publisher.failNext();
    var failed = relay.publishAvailable();

    assertThat(failed).isEqualTo(new PublicationBatchResult(1, 0, 1));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT published_at IS NULL FROM outbox_messages WHERE id = ?",
                Boolean.class,
                messageId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_messages WHERE id = ?", Integer.class, messageId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT last_error FROM outbox_messages WHERE id = ?", String.class, messageId))
        .contains("broker unavailable");

    jdbcTemplate.update(
        """
        UPDATE outbox_messages
        SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
        WHERE id = ?
        """,
        messageId);
    var recovered = relay.publishAvailable();

    assertThat(recovered).isEqualTo(new PublicationBatchResult(1, 1, 0));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT published_at IS NOT NULL FROM outbox_messages WHERE id = ?",
                Boolean.class,
                messageId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM outbox_messages WHERE id = ?", Integer.class, messageId))
        .isEqualTo(2);
    assertThat(publisher.attemptedMessageIds()).containsExactly(messageId, messageId);

    assertThat(relay.publishAvailable()).isEqualTo(new PublicationBatchResult(0, 0, 0));
    assertThat(publisher.attemptedMessageIds()).containsExactly(messageId, messageId);
  }

  @Test
  void createsOneOutboxIntentForIdempotentIngressReplays() throws Exception {
    var firstEventId = createEvent("outbox-idempotency-1");
    var replayedEventId = createEvent("outbox-idempotency-1");

    assertThat(replayedEventId).isEqualTo(firstEventId);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_messages", Integer.class))
        .isEqualTo(1);
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
    assertThat(result.getResponse().getStatus()).isIn(200, 201);
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class PublisherConfiguration {

    @Bean
    @Primary
    RecordingPublisher recordingPublisher() {
      return new RecordingPublisher();
    }
  }

  static final class RecordingPublisher implements MessagePublisher {

    private final List<UUID> attemptedMessageIds = new ArrayList<>();
    private boolean failNext;

    @Override
    public void publish(OutboxMessage message, Duration timeout) throws Exception {
      attemptedMessageIds.add(message.id());
      if (failNext) {
        failNext = false;
        throw new IOException("broker unavailable");
      }
    }

    void failNext() {
      failNext = true;
    }

    List<UUID> attemptedMessageIds() {
      return List.copyOf(attemptedMessageIds);
    }

    void reset() {
      attemptedMessageIds.clear();
      failNext = false;
    }
  }
}
