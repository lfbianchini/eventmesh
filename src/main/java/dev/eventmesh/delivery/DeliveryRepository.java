package dev.eventmesh.delivery;

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
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DeliveryRepository {

  private final JdbcClient jdbcClient;

  public DeliveryRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public boolean insertInitial(
      UUID id,
      UUID tenantId,
      UUID sourceMessageId,
      UUID eventId,
      UUID subscriptionId,
      Instant createdAt) {
    return jdbcClient
            .sql(
                """
                INSERT INTO deliveries (
                  id,
                  tenant_id,
                  source_message_id,
                  event_id,
                  subscription_id,
                  status,
                  next_attempt_at,
                  created_at
                )
                VALUES (
                  :id,
                  :tenantId,
                  :sourceMessageId,
                  :eventId,
                  :subscriptionId,
                  'PENDING',
                  :createdAt,
                  :createdAt
                )
                ON CONFLICT (source_message_id, subscription_id)
                  WHERE replay_of_delivery_id IS NULL
                DO NOTHING
                """)
            .param("id", id)
            .param("tenantId", tenantId)
            .param("sourceMessageId", sourceMessageId)
            .param("eventId", eventId)
            .param("subscriptionId", subscriptionId)
            .param("createdAt", utc(createdAt))
            .update()
        == 1;
  }

  public DeliverySummary insertReplay(DeliverySummary original, UUID replayId, Instant createdAt) {
    return jdbcClient
        .sql(
            """
            INSERT INTO deliveries (
              id,
              tenant_id,
              source_message_id,
              event_id,
              subscription_id,
              replay_of_delivery_id,
              status,
              next_attempt_at,
              created_at
            )
            SELECT
              :replayId,
              tenant_id,
              source_message_id,
              event_id,
              subscription_id,
              id,
              'PENDING',
              :createdAt,
              :createdAt
            FROM deliveries
            WHERE id = :originalId
            RETURNING
              id,
              event_id,
              subscription_id,
              replay_of_delivery_id,
              status,
              attempt_count,
              next_attempt_at,
              last_error,
              created_at,
              completed_at
            """)
        .param("replayId", replayId)
        .param("createdAt", utc(createdAt))
        .param("originalId", original.id())
        .query(this::mapSummary)
        .single();
  }

  @Transactional
  public List<DeliveryWork> claimBatch(
      UUID workerId, Instant now, Instant claimedUntil, int batchSize) {
    return jdbcClient
        .sql(
            """
            WITH candidates AS (
              SELECT id
              FROM deliveries
              WHERE status IN ('PENDING', 'RETRYING')
                AND next_attempt_at <= :now
                AND (claimed_until IS NULL OR claimed_until < :now)
              ORDER BY next_attempt_at, created_at, id
              FOR UPDATE SKIP LOCKED
              LIMIT :batchSize
            )
            UPDATE deliveries AS delivery
            SET claimed_by = :workerId,
                claimed_until = :claimedUntil
            FROM candidates
            WHERE delivery.id = candidates.id
            RETURNING
              delivery.id,
              delivery.tenant_id,
              delivery.source_message_id,
              delivery.event_id,
              delivery.subscription_id,
              (SELECT endpoint_url FROM subscriptions WHERE id = delivery.subscription_id)
                AS endpoint_url,
              (SELECT signing_secret_ciphertext FROM subscriptions
                WHERE id = delivery.subscription_id) AS signing_secret_ciphertext,
              (SELECT event_type FROM events WHERE id = delivery.event_id) AS event_type,
              (SELECT event_source FROM events WHERE id = delivery.event_id) AS event_source,
              (SELECT subject FROM events WHERE id = delivery.event_id) AS subject,
              (SELECT data::text FROM events WHERE id = delivery.event_id) AS event_data,
              (SELECT received_at FROM events WHERE id = delivery.event_id) AS received_at,
              delivery.attempt_count
            """)
        .param("workerId", workerId)
        .param("now", utc(now))
        .param("claimedUntil", utc(claimedUntil))
        .param("batchSize", batchSize)
        .query(this::mapWork)
        .list();
  }

  @Transactional
  public boolean recordSuccess(
      DeliveryWork work, UUID workerId, Instant startedAt, Instant finishedAt, int responseStatus) {
    var updated =
        jdbcClient
            .sql(
                """
                UPDATE deliveries
                SET status = 'SUCCEEDED',
                    attempt_count = attempt_count + 1,
                    last_error = NULL,
                    completed_at = :finishedAt,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = :deliveryId
                  AND claimed_by = :workerId
                  AND status IN ('PENDING', 'RETRYING')
                """)
            .param("finishedAt", utc(finishedAt))
            .param("deliveryId", work.id())
            .param("workerId", workerId)
            .update();
    if (updated == 0) {
      return false;
    }
    insertAttempt(
        work.id(),
        work.attemptCount() + 1,
        startedAt,
        finishedAt,
        "SUCCEEDED",
        responseStatus,
        null);
    return true;
  }

  @Transactional
  public boolean recordFailure(
      DeliveryWork work,
      UUID workerId,
      Instant startedAt,
      Instant finishedAt,
      String outcome,
      Integer responseStatus,
      String errorMessage,
      boolean terminal,
      Instant nextAttemptAt) {
    var updated =
        jdbcClient
            .sql(
                """
                UPDATE deliveries
                SET status = :status,
                    attempt_count = attempt_count + 1,
                    next_attempt_at = :nextAttemptAt,
                    last_error = :errorMessage,
                    completed_at = :completedAt,
                    claimed_by = NULL,
                    claimed_until = NULL
                WHERE id = :deliveryId
                  AND claimed_by = :workerId
                  AND status IN ('PENDING', 'RETRYING')
                """)
            .param("status", terminal ? DeliveryStatus.DEAD_LETTER.name() : DeliveryStatus.RETRYING.name())
            .param("nextAttemptAt", utc(nextAttemptAt))
            .param("errorMessage", errorMessage)
            .param("completedAt", terminal ? utc(finishedAt) : null)
            .param("deliveryId", work.id())
            .param("workerId", workerId)
            .update();
    if (updated == 0) {
      return false;
    }
    insertAttempt(
        work.id(),
        work.attemptCount() + 1,
        startedAt,
        finishedAt,
        outcome,
        responseStatus,
        errorMessage);
    return true;
  }

  public List<DeliverySummary> findAllByTenant(UUID tenantId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              event_id,
              subscription_id,
              replay_of_delivery_id,
              status,
              attempt_count,
              next_attempt_at,
              last_error,
              created_at,
              completed_at
            FROM deliveries
            WHERE tenant_id = :tenantId
            ORDER BY created_at DESC, id
            LIMIT 100
            """)
        .param("tenantId", tenantId)
        .query(this::mapSummary)
        .list();
  }

  public Optional<DeliverySummary> findByIdAndTenant(UUID deliveryId, UUID tenantId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              event_id,
              subscription_id,
              replay_of_delivery_id,
              status,
              attempt_count,
              next_attempt_at,
              last_error,
              created_at,
              completed_at
            FROM deliveries
            WHERE id = :deliveryId
              AND tenant_id = :tenantId
            """)
        .param("deliveryId", deliveryId)
        .param("tenantId", tenantId)
        .query(this::mapSummary)
        .optional();
  }

  public List<DeliveryAttempt> findAttempts(UUID deliveryId) {
    return jdbcClient
        .sql(
            """
            SELECT
              id,
              attempt_number,
              started_at,
              finished_at,
              outcome,
              response_status,
              error_message
            FROM delivery_attempts
            WHERE delivery_id = :deliveryId
            ORDER BY attempt_number
            """)
        .param("deliveryId", deliveryId)
        .query(this::mapAttempt)
        .list();
  }

  private void insertAttempt(
      UUID deliveryId,
      int attemptNumber,
      Instant startedAt,
      Instant finishedAt,
      String outcome,
      Integer responseStatus,
      String errorMessage) {
    jdbcClient
        .sql(
            """
            INSERT INTO delivery_attempts (
              id,
              delivery_id,
              attempt_number,
              started_at,
              finished_at,
              outcome,
              response_status,
              error_message
            )
            VALUES (
              :id,
              :deliveryId,
              :attemptNumber,
              :startedAt,
              :finishedAt,
              :outcome,
              :responseStatus,
              :errorMessage
            )
            """)
        .param("id", UUID.randomUUID())
        .param("deliveryId", deliveryId)
        .param("attemptNumber", attemptNumber)
        .param("startedAt", utc(startedAt))
        .param("finishedAt", utc(finishedAt))
        .param("outcome", outcome)
        .param("responseStatus", responseStatus)
        .param("errorMessage", errorMessage)
        .update();
  }

  private DeliveryWork mapWork(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DeliveryWork(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getObject("source_message_id", UUID.class),
        resultSet.getObject("event_id", UUID.class),
        resultSet.getObject("subscription_id", UUID.class),
        resultSet.getString("endpoint_url"),
        resultSet.getString("signing_secret_ciphertext"),
        resultSet.getString("event_type"),
        resultSet.getString("event_source"),
        resultSet.getString("subject"),
        resultSet.getString("event_data"),
        resultSet.getObject("received_at", OffsetDateTime.class).toInstant(),
        resultSet.getInt("attempt_count"));
  }

  private DeliverySummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DeliverySummary(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("event_id", UUID.class),
        resultSet.getObject("subscription_id", UUID.class),
        resultSet.getObject("replay_of_delivery_id", UUID.class),
        DeliveryStatus.valueOf(resultSet.getString("status")),
        resultSet.getInt("attempt_count"),
        instantOrNull(resultSet, "next_attempt_at"),
        resultSet.getString("last_error"),
        resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
        instantOrNull(resultSet, "completed_at"));
  }

  private DeliveryAttempt mapAttempt(ResultSet resultSet, int rowNumber) throws SQLException {
    var responseStatus = resultSet.getObject("response_status", Integer.class);
    return new DeliveryAttempt(
        resultSet.getObject("id", UUID.class),
        resultSet.getInt("attempt_number"),
        resultSet.getObject("started_at", OffsetDateTime.class).toInstant(),
        resultSet.getObject("finished_at", OffsetDateTime.class).toInstant(),
        resultSet.getString("outcome"),
        responseStatus,
        resultSet.getString("error_message"));
  }

  private Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    var value = resultSet.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }

  private OffsetDateTime utc(Instant instant) {
    return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
