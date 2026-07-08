package dev.eventmesh.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EventIngestionRepository {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  public EventIngestionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
  }

  Optional<StoredEvent> insertIfAbsent(
      UUID eventId,
      UUID tenantId,
      String idempotencyKey,
      IngestEventRequest request,
      String payloadHash,
      Instant receivedAt) {
    return jdbcClient
        .sql(
            """
            INSERT INTO events (
              id,
              tenant_id,
              idempotency_key,
              event_type,
              event_source,
              subject,
              data,
              payload_hash,
              received_at
            )
            VALUES (
              :id,
              :tenantId,
              :idempotencyKey,
              :eventType,
              :eventSource,
              :subject,
              CAST(:data AS jsonb),
              :payloadHash,
              :receivedAt
            )
            ON CONFLICT (tenant_id, idempotency_key) DO NOTHING
            RETURNING
              id,
              tenant_id,
              idempotency_key,
              event_type,
              event_source,
              subject,
              data::text AS data,
              payload_hash,
              received_at
            """)
        .param("id", eventId)
        .param("tenantId", tenantId)
        .param("idempotencyKey", idempotencyKey)
        .param("eventType", request.type())
        .param("eventSource", request.source())
        .param("subject", request.subject())
        .param("data", request.data().toString())
        .param("payloadHash", payloadHash)
        .param("receivedAt", OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC))
        .query(this::mapEvent)
        .optional();
  }

  Optional<StoredEvent> findByIdempotencyKey(UUID tenantId, String idempotencyKey) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              tenant_id,
              idempotency_key,
              event_type,
              event_source,
              subject,
              data::text AS data,
              payload_hash,
              received_at
            FROM events
            WHERE tenant_id = :tenantId
              AND idempotency_key = :idempotencyKey
            """)
        .param("tenantId", tenantId)
        .param("idempotencyKey", idempotencyKey)
        .query(this::mapEvent)
        .optional();
  }

  Optional<StoredEvent> findByIdAndTenantId(UUID eventId, UUID tenantId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              tenant_id,
              idempotency_key,
              event_type,
              event_source,
              subject,
              data::text AS data,
              payload_hash,
              received_at
            FROM events
            WHERE id = :eventId
              AND tenant_id = :tenantId
            """)
        .param("eventId", eventId)
        .param("tenantId", tenantId)
        .query(this::mapEvent)
        .optional();
  }

  private StoredEvent mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
    try {
      return new StoredEvent(
          resultSet.getObject("id", UUID.class),
          resultSet.getObject("tenant_id", UUID.class),
          resultSet.getString("idempotency_key"),
          resultSet.getString("event_type"),
          resultSet.getString("event_source"),
          resultSet.getString("subject"),
          objectMapper.readTree(resultSet.getString("data")),
          resultSet.getString("payload_hash"),
          resultSet.getObject("received_at", OffsetDateTime.class).toInstant());
    } catch (JsonProcessingException exception) {
      throw new SQLException("Stored event data is not valid JSON", exception);
    }
  }
}

