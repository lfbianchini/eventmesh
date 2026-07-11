package dev.eventmesh.publication;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OutboxRepository {

  private final JdbcClient jdbcClient;

  public OutboxRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public void insert(
      UUID id,
      UUID tenantId,
      UUID eventId,
      String topic,
      String partitionKey,
      String messageType,
      int schemaVersion,
      String payload,
      Instant createdAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO outbox_messages (
              id,
              tenant_id,
              event_id,
              topic,
              partition_key,
              message_type,
              schema_version,
              payload,
              created_at,
              next_attempt_at
            )
            VALUES (
              :id,
              :tenantId,
              :eventId,
              :topic,
              :partitionKey,
              :messageType,
              :schemaVersion,
              CAST(:payload AS jsonb),
              :createdAt,
              :createdAt
            )
            """)
        .param("id", id)
        .param("tenantId", tenantId)
        .param("eventId", eventId)
        .param("topic", topic)
        .param("partitionKey", partitionKey)
        .param("messageType", messageType)
        .param("schemaVersion", schemaVersion)
        .param("payload", payload)
        .param("createdAt", utc(createdAt))
        .update();
  }

  @Transactional
  public List<OutboxMessage> claimBatch(
      UUID workerId, Instant now, Instant claimedUntil, int batchSize) {
    return jdbcClient
        .sql(
            """
            WITH candidates AS (
              SELECT id
              FROM outbox_messages
              WHERE published_at IS NULL
                AND next_attempt_at <= :now
                AND (claimed_until IS NULL OR claimed_until < :now)
              ORDER BY created_at, id
              FOR UPDATE SKIP LOCKED
              LIMIT :batchSize
            )
            UPDATE outbox_messages AS message
            SET claimed_by = :workerId,
                claimed_until = :claimedUntil
            FROM candidates
            WHERE message.id = candidates.id
            RETURNING
              message.id,
              message.tenant_id,
              message.event_id,
              message.topic,
              message.partition_key,
              message.message_type,
              message.schema_version,
              message.payload::text AS payload,
              message.created_at,
              message.attempt_count
            """)
        .param("workerId", workerId)
        .param("now", utc(now))
        .param("claimedUntil", utc(claimedUntil))
        .param("batchSize", batchSize)
        .query(this::mapMessage)
        .list();
  }

  public boolean markPublished(UUID messageId, UUID workerId, Instant publishedAt) {
    return jdbcClient
            .sql(
                """
                UPDATE outbox_messages
                SET published_at = :publishedAt,
                    attempt_count = attempt_count + 1,
                    last_error = NULL,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = :messageId
                  AND claimed_by = :workerId
                  AND published_at IS NULL
                """)
            .param("publishedAt", utc(publishedAt))
            .param("messageId", messageId)
            .param("workerId", workerId)
            .update()
        == 1;
  }

  public boolean markFailed(
      UUID messageId, UUID workerId, Instant nextAttemptAt, String lastError) {
    return jdbcClient
            .sql(
                """
                UPDATE outbox_messages
                SET attempt_count = attempt_count + 1,
                    next_attempt_at = :nextAttemptAt,
                    last_error = :lastError,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = :messageId
                  AND claimed_by = :workerId
                  AND published_at IS NULL
                """)
            .param("nextAttemptAt", utc(nextAttemptAt))
            .param("lastError", lastError)
            .param("messageId", messageId)
            .param("workerId", workerId)
            .update()
        == 1;
  }

  private OutboxMessage mapMessage(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OutboxMessage(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getObject("event_id", UUID.class),
        resultSet.getString("topic"),
        resultSet.getString("partition_key"),
        resultSet.getString("message_type"),
        resultSet.getInt("schema_version"),
        resultSet.getString("payload"),
        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
        resultSet.getInt("attempt_count"));
  }

  private OffsetDateTime utc(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
