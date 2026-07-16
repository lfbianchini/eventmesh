package dev.eventmesh.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SubscriptionRepository {

  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public SubscriptionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  public StoredSubscription insert(
      UUID id,
      UUID tenantId,
      String name,
      String endpointUrl,
      List<String> eventTypes,
      String signingSecretCiphertext,
      Instant createdAt) {
    try {
      return jdbcClient
          .sql(
              """
              INSERT INTO subscriptions (
                id,
                tenant_id,
                name,
                endpoint_url,
                event_types,
                signing_secret_ciphertext,
                created_at
              )
              VALUES (
                :id,
                :tenantId,
                :name,
                :endpointUrl,
                CAST(:eventTypes AS jsonb),
                :signingSecretCiphertext,
                :createdAt
              )
              RETURNING
                id,
                tenant_id,
                name,
                endpoint_url,
                event_types::text AS event_types,
                signing_secret_ciphertext,
                enabled,
                created_at
              """)
          .param("id", id)
          .param("tenantId", tenantId)
          .param("name", name)
          .param("endpointUrl", endpointUrl)
          .param("eventTypes", objectMapper.writeValueAsString(eventTypes))
          .param("signingSecretCiphertext", signingSecretCiphertext)
          .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
          .query(this::mapSubscription)
          .single();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Subscription event types could not be serialized", exception);
    }
  }

  public List<StoredSubscription> findAllByTenant(UUID tenantId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              tenant_id,
              name,
              endpoint_url,
              event_types::text AS event_types,
              signing_secret_ciphertext,
              enabled,
              created_at
            FROM subscriptions
            WHERE tenant_id = :tenantId
            ORDER BY created_at, id
            """)
        .param("tenantId", tenantId)
        .query(this::mapSubscription)
        .list();
  }

  public Optional<StoredSubscription> findByIdAndTenant(UUID id, UUID tenantId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              tenant_id,
              name,
              endpoint_url,
              event_types::text AS event_types,
              signing_secret_ciphertext,
              enabled,
              created_at
            FROM subscriptions
            WHERE id = :id
              AND tenant_id = :tenantId
            """)
        .param("id", id)
        .param("tenantId", tenantId)
        .query(this::mapSubscription)
        .optional();
  }

  public List<StoredSubscription> findEnabledMatching(UUID tenantId, String eventType) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              tenant_id,
              name,
              endpoint_url,
              event_types::text AS event_types,
              signing_secret_ciphertext,
              enabled,
              created_at
            FROM subscriptions
            WHERE tenant_id = :tenantId
              AND enabled = TRUE
              AND jsonb_exists(event_types, :eventType)
            ORDER BY created_at, id
            """)
        .param("tenantId", tenantId)
        .param("eventType", eventType)
        .query(this::mapSubscription)
        .list();
  }

  private StoredSubscription mapSubscription(ResultSet resultSet, int rowNumber)
      throws SQLException {
    try {
      return new StoredSubscription(
          resultSet.getObject("id", UUID.class),
          resultSet.getObject("tenant_id", UUID.class),
          resultSet.getString("name"),
          resultSet.getString("endpoint_url"),
          objectMapper.readValue(resultSet.getString("event_types"), STRING_LIST),
          resultSet.getString("signing_secret_ciphertext"),
          resultSet.getBoolean("enabled"),
          resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    } catch (JsonProcessingException exception) {
      throw new SQLException("Stored subscription event types are invalid JSON", exception);
    }
  }
}
