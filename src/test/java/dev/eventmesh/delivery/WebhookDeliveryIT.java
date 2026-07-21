package dev.eventmesh.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    properties = {
      "eventmesh.outbox.scheduling-enabled=false",
      "eventmesh.kafka.create-topics=false",
      "eventmesh.webhooks.kafka-listener-enabled=false",
      "eventmesh.webhooks.scheduling-enabled=false",
      "eventmesh.webhooks.max-attempts=3",
      "eventmesh.webhooks.retry-base-delay=10ms",
      "eventmesh.webhooks.retry-max-delay=100ms"
    })
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WebhookDeliveryIT {

  private static final String LOCAL_API_KEY = "em_dev_local_key_change_me";
  private static final String SIGNING_SECRET = "test-signing-secret-at-least-32-characters";
  private static final FakeSubscriber SUBSCRIBER = new FakeSubscriber();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("eventmesh")
          .withUsername("eventmesh")
          .withPassword("eventmesh")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(30)));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeAll
  static void startSubscriber() throws IOException {
    SUBSCRIBER.start();
  }

  @AfterAll
  static void stopSubscriber() {
    SUBSCRIBER.stop();
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired AcceptedEventFanout fanout;
  @Autowired DeliveryWorker worker;

  @BeforeEach
  void reset() {
    jdbcTemplate.update("DELETE FROM delivery_attempts");
    jdbcTemplate.update("DELETE FROM deliveries");
    jdbcTemplate.update("DELETE FROM subscriptions");
    jdbcTemplate.update("DELETE FROM outbox_messages");
    jdbcTemplate.update("DELETE FROM events");
    SUBSCRIBER.reset();
  }

  @Test
  void createsTenantScopedSubscriptionsAndReturnsTheSecretOnlyOnce() throws Exception {
    var created = createSubscription("orders", List.of("order.created"));
    var subscriptionId = created.get("id").asText();

    assertThat(created.get("signingSecret").asText()).isEqualTo(SIGNING_SECRET);
    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT signing_secret_ciphertext
                FROM subscriptions
                WHERE id = ?
                """,
                String.class,
                UUID.fromString(subscriptionId)))
        .doesNotContain(SIGNING_SECRET);

    mockMvc
        .perform(get("/v1/subscriptions").header("X-EventMesh-Key", LOCAL_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(subscriptionId))
        .andExpect(jsonPath("$[0].signingSecret").doesNotExist());

    mockMvc
        .perform(
            get("/v1/subscriptions/{id}", UUID.randomUUID())
                .header("X-EventMesh-Key", LOCAL_API_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));

    mockMvc
        .perform(
            post("/v1/subscriptions")
                .header("X-EventMesh-Key", LOCAL_API_KEY)
                .contentType("application/json")
                .content(
                    """
                    {
                      "name": "orders",
                      "endpointUrl": "%s",
                      "eventTypes": ["order.created"],
                      "signingSecret": "%s"
                    }
                    """
                        .formatted(SUBSCRIBER.endpoint(), SIGNING_SECRET)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SUBSCRIPTION_CONFLICT"));
  }

  @Test
  void filtersSubscriptionsAndDeduplicatesRepeatedKafkaConsumption() throws Exception {
    createSubscription("orders", List.of("order.created"));
    createSubscription("refunds", List.of("refund.created"));
    var eventId = createEvent("fanout-dedup-1");
    var payload = outboxPayload(eventId);

    assertThat(fanout.accept(payload)).isEqualTo(1);
    assertThat(fanout.accept(payload)).isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM deliveries", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void signsAndRecordsSuccessfulWebhookDelivery() throws Exception {
    createSubscription("orders", List.of("order.created"));
    var eventId = createEvent("signed-delivery-1");
    fanout.accept(outboxPayload(eventId));

    var result = worker.deliverAvailable();
    var deliveryId = onlyDeliveryId();

    assertThat(result).isEqualTo(new DeliveryBatchResult(1, 1, 0, 0));
    assertThat(SUBSCRIBER.requests()).hasSize(1);
    assertThat(SUBSCRIBER.requests().getFirst().validSignature()).isTrue();
    assertThat(SUBSCRIBER.requests().getFirst().body().at("/id").asText()).isEqualTo(eventId);

    mockMvc
        .perform(
            get("/v1/deliveries/{id}", deliveryId)
                .header("X-EventMesh-Key", LOCAL_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.delivery.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.delivery.attemptCount").value(1))
        .andExpect(jsonPath("$.attempts[0].outcome").value("SUCCEEDED"))
        .andExpect(jsonPath("$.attempts[0].responseStatus").value(204));
  }

  @Test
  void retriesTransientFailuresThenSucceedsWithImmutableAttemptHistory() throws Exception {
    SUBSCRIBER.respondWith(500, 429, 204);
    createSubscription("orders", List.of("order.created"));
    var eventId = createEvent("retry-delivery-1");
    fanout.accept(outboxPayload(eventId));
    var deliveryId = onlyDeliveryId();

    assertThat(worker.deliverAvailable()).isEqualTo(new DeliveryBatchResult(1, 0, 1, 0));
    makeDeliveryDue(deliveryId);
    assertThat(worker.deliverAvailable()).isEqualTo(new DeliveryBatchResult(1, 0, 1, 0));
    makeDeliveryDue(deliveryId);
    assertThat(worker.deliverAvailable()).isEqualTo(new DeliveryBatchResult(1, 1, 0, 0));

    var attempts =
        jdbcTemplate.queryForList(
            """
            SELECT outcome
            FROM delivery_attempts
            WHERE delivery_id = ?
            ORDER BY attempt_number
            """,
            String.class,
            deliveryId);
    assertThat(attempts)
        .containsExactly("RETRYABLE_FAILURE", "RETRYABLE_FAILURE", "SUCCEEDED");
    assertThat(SUBSCRIBER.requests().stream().map(RequestRecord::attempt).toList())
        .containsExactly(1, 2, 3);
  }

  @Test
  void deadLettersPermanentFailureAndReplaysWithoutMutatingHistory() throws Exception {
    SUBSCRIBER.respondWith(400, 204);
    createSubscription("orders", List.of("order.created"));
    var eventId = createEvent("dead-letter-replay-1");
    fanout.accept(outboxPayload(eventId));
    var originalId = onlyDeliveryId();

    assertThat(worker.deliverAvailable()).isEqualTo(new DeliveryBatchResult(1, 0, 0, 1));
    var replayResult =
        mockMvc
            .perform(
                post("/v1/deliveries/{id}/replay", originalId)
                    .header("X-EventMesh-Key", LOCAL_API_KEY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.replayOfDeliveryId").value(originalId.toString()))
            .andReturn();
    var replayId =
        UUID.fromString(
            objectMapper
                .readTree(replayResult.getResponse().getContentAsString())
                .get("id")
                .asText());

    assertThat(worker.deliverAvailable()).isEqualTo(new DeliveryBatchResult(1, 1, 0, 0));
    assertThat(statusOf(originalId)).isEqualTo("DEAD_LETTER");
    assertThat(statusOf(replayId)).isEqualTo("SUCCEEDED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery_attempts WHERE delivery_id = ?",
                Integer.class,
                originalId))
        .isEqualTo(1);
  }

  private JsonNode createSubscription(String name, List<String> eventTypes) throws Exception {
    var eventTypesJson = objectMapper.writeValueAsString(eventTypes);
    var result =
        mockMvc
            .perform(
                post("/v1/subscriptions")
                    .header("X-EventMesh-Key", LOCAL_API_KEY)
                    .contentType("application/json")
                    .content(
                        """
                        {
                          "name": "%s",
                          "endpointUrl": "%s",
                          "eventTypes": %s,
                          "signingSecret": "%s"
                        }
                        """
                            .formatted(
                                name, SUBSCRIBER.endpoint(), eventTypesJson, SIGNING_SECRET)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
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
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String outboxPayload(String eventId) {
    return jdbcTemplate.queryForObject(
        "SELECT payload::text FROM outbox_messages WHERE event_id = ?",
        String.class,
        UUID.fromString(eventId));
  }

  private UUID onlyDeliveryId() {
    return jdbcTemplate.queryForObject("SELECT id FROM deliveries", UUID.class);
  }

  private void makeDeliveryDue(UUID deliveryId) {
    jdbcTemplate.update(
        """
        UPDATE deliveries
        SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
        WHERE id = ?
        """,
        deliveryId);
  }

  private String statusOf(UUID deliveryId) {
    return jdbcTemplate.queryForObject(
        "SELECT status FROM deliveries WHERE id = ?", String.class, deliveryId);
  }

  record RequestRecord(JsonNode body, int attempt, boolean validSignature) {}

  static final class FakeSubscriber {

    private final ArrayDeque<Integer> responseStatuses = new ArrayDeque<>();
    private final List<RequestRecord> requests = new ArrayList<>();
    private HttpServer server;

    void start() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/webhooks", this::handle);
      server.setExecutor(Executors.newCachedThreadPool());
      server.start();
    }

    void stop() {
      if (server != null) {
        server.stop(0);
      }
    }

    String endpoint() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks";
    }

    synchronized void reset() {
      responseStatuses.clear();
      requests.clear();
    }

    synchronized void respondWith(int... statuses) {
      for (var status : statuses) {
        responseStatuses.add(status);
      }
    }

    synchronized List<RequestRecord> requests() {
      return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
      var bodyBytes = exchange.getRequestBody().readAllBytes();
      var body = new String(bodyBytes, StandardCharsets.UTF_8);
      var deliveryId = exchange.getRequestHeaders().getFirst("X-EventMesh-Delivery");
      var timestamp = exchange.getRequestHeaders().getFirst("X-EventMesh-Timestamp");
      var signature = exchange.getRequestHeaders().getFirst("X-EventMesh-Signature");
      var attempt = Integer.parseInt(exchange.getRequestHeaders().getFirst("X-EventMesh-Attempt"));
      boolean valid = signature.equals(expectedSignature(timestamp, deliveryId, body));
      try {
        synchronized (this) {
          requests.add(new RequestRecord(new ObjectMapper().readTree(body), attempt, valid));
        }
      } catch (Exception exception) {
        throw new IOException(exception);
      }
      int status;
      synchronized (this) {
        status = responseStatuses.isEmpty() ? 204 : responseStatuses.removeFirst();
      }
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    }

    private String expectedSignature(String timestamp, String deliveryId, String body) {
      try {
        var canonical = timestamp + "." + deliveryId + "." + body;
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(
            new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1="
            + HexFormat.of()
                .formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }
  }
}
