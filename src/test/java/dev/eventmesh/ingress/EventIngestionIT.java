package dev.eventmesh.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eventmesh.auth.ApiKeyHasher;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EventIngestionIT {

  private static final String LOCAL_API_KEY = "em_dev_local_key_change_me";
  private static final String SECOND_API_KEY = "em_test_second_tenant_key";

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
    registry.add("eventmesh.outbox.scheduling-enabled", () -> "false");
    registry.add("eventmesh.kafka.create-topics", () -> "false");
    registry.add("eventmesh.webhooks.kafka-listener-enabled", () -> "false");
    registry.add("eventmesh.webhooks.scheduling-enabled", () -> "false");
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetEventsAndCreateSecondTenant() {
    jdbcTemplate.update("DELETE FROM outbox_messages");
    jdbcTemplate.update("DELETE FROM events");
    jdbcTemplate.update(
        """
        INSERT INTO tenants (id, slug, display_name)
        VALUES (
          '22222222-2222-2222-2222-222222222222',
          'second-test-tenant',
          'Second Test Tenant'
        )
        ON CONFLICT (id) DO NOTHING
        """);
    jdbcTemplate.update(
        """
        INSERT INTO tenant_api_keys (id, tenant_id, key_hash, label)
        VALUES (
          '22222222-2222-2222-2222-222222222223',
          '22222222-2222-2222-2222-222222222222',
          ?,
          'Second tenant test key'
        )
        ON CONFLICT (id) DO NOTHING
        """,
        ApiKeyHasher.sha256(SECOND_API_KEY));
  }

  @Test
  void createsAndReadsADurableEvent() throws Exception {
    var created =
        mockMvc
            .perform(
                post("/v1/events")
                    .header("X-EventMesh-Key", LOCAL_API_KEY)
                    .header("Idempotency-Key", "create-order-123")
                    .contentType("application/json")
                    .content(validEventJson(4200)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/v1/events/")))
            .andExpect(jsonPath("$.type").value("order.created"))
            .andExpect(jsonPath("$.data.amount").value(4200))
            .andExpect(jsonPath("$.replayed").value(false))
            .andReturn();

    var eventId = responseJson(created.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            get("/v1/events/{eventId}", eventId).header("X-EventMesh-Key", LOCAL_API_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(eventId))
        .andExpect(jsonPath("$.subject").value("order-123"));
  }

  @Test
  void replaysTheOriginalEventForEquivalentRequests() throws Exception {
    var first =
        postEvent(LOCAL_API_KEY, "safe-retry-1", validEventJson(4200), 201)
            .get("id")
            .asText();

    var reorderedJson =
        """
        {
          "source": "demo.checkout",
          "type": "order.created",
          "data": {"currency": "USD", "amount": 4200},
          "subject": "order-123"
        }
        """;
    var replayed = postEvent(LOCAL_API_KEY, "safe-retry-1", reorderedJson, 200);

    assertThat(replayed.get("id").asText()).isEqualTo(first);
    assertThat(replayed.get("replayed").asBoolean()).isTrue();
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_messages", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void rejectsReuseOfAnIdempotencyKeyForDifferentPayload() throws Exception {
    postEvent(LOCAL_API_KEY, "conflicting-retry-1", validEventJson(4200), 201);

    mockMvc
        .perform(
            post("/v1/events")
                .header("X-EventMesh-Key", LOCAL_API_KEY)
                .header("Idempotency-Key", "conflicting-retry-1")
                .contentType("application/json")
                .content(validEventJson(9900)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void requiresAValidApiKey() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .header("Idempotency-Key", "unauthorized-1")
                .contentType("application/json")
                .content(validEventJson(4200)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mockMvc
        .perform(
            post("/v1/events")
                .header("X-EventMesh-Key", "not-a-real-key")
                .header("Idempotency-Key", "unauthorized-2")
                .contentType("application/json")
                .content(validEventJson(4200)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void validatesHeadersAndBody() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .header("X-EventMesh-Key", LOCAL_API_KEY)
                .contentType("application/json")
                .content(validEventJson(4200)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    mockMvc
        .perform(
            post("/v1/events")
                .header("X-EventMesh-Key", LOCAL_API_KEY)
                .header("Idempotency-Key", "invalid-body-1")
                .contentType("application/json")
                .content(
                    """
                    {"type":"Order Created","source":"","data":null}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.type").exists())
        .andExpect(jsonPath("$.errors.source").exists())
        .andExpect(jsonPath("$.errors.data").exists());
  }

  @Test
  void hidesEventsFromOtherTenants() throws Exception {
    var eventId =
        postEvent(LOCAL_API_KEY, "tenant-isolation-1", validEventJson(4200), 201)
            .get("id")
            .asText();

    mockMvc
        .perform(
            get("/v1/events/{eventId}", eventId).header("X-EventMesh-Key", SECOND_API_KEY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
  }

  @Test
  void serializesConcurrentSameKeyRequestsToOneEvent() throws Exception {
    int requestCount = 8;
    var executor = Executors.newFixedThreadPool(requestCount);
    try {
      var tasks = new ArrayList<Callable<JsonNode>>();
      for (int index = 0; index < requestCount; index++) {
        tasks.add(
            () ->
                postEvent(
                    LOCAL_API_KEY, "concurrent-safe-retry-1", validEventJson(4200), null));
      }

      var responses = executor.invokeAll(tasks);
      var eventIds = new HashSet<String>();
      for (var response : responses) {
        eventIds.add(response.get().get("id").asText());
      }

      assertThat(eventIds).hasSize(1);
      assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private JsonNode postEvent(
      String apiKey, String idempotencyKey, String json, Integer expectedStatus) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/v1/events")
                    .header("X-EventMesh-Key", apiKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType("application/json")
                    .content(json))
            .andReturn();

    if (expectedStatus == null) {
      assertThat(result.getResponse().getStatus()).isIn(200, 201);
    } else {
      assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
    }
    return responseJson(result.getResponse().getContentAsString());
  }

  private JsonNode responseJson(String response) throws Exception {
    return objectMapper.readTree(response);
  }

  private String validEventJson(int amount) {
    return """
        {
          "type": "order.created",
          "source": "demo.checkout",
          "subject": "order-123",
          "data": {"amount": %d, "currency": "USD"}
        }
        """
        .formatted(amount);
  }
}
