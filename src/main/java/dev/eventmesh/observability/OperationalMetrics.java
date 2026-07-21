package dev.eventmesh.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {

  private static final Logger LOGGER = LoggerFactory.getLogger(OperationalMetrics.class);

  private final JdbcTemplate jdbcTemplate;
  private final AtomicLong acceptedEvents = new AtomicLong();
  private final AtomicLong pendingOutbox = new AtomicLong();
  private final AtomicLong pendingDeliveries = new AtomicLong();
  private final AtomicLong retryingDeliveries = new AtomicLong();
  private final AtomicLong succeededDeliveries = new AtomicLong();
  private final AtomicLong deadLetterDeliveries = new AtomicLong();

  public OperationalMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    registerGauge(
        meterRegistry,
        "eventmesh.state.events",
        "Durable events accepted by EventMesh",
        acceptedEvents);
    registerGauge(
        meterRegistry,
        "eventmesh.state.outbox",
        "Unpublished transactional outbox messages",
        pendingOutbox,
        "status",
        "pending");
    registerGauge(
        meterRegistry,
        "eventmesh.state.deliveries",
        "Durable webhook deliveries by current state",
        pendingDeliveries,
        "status",
        "pending");
    registerGauge(
        meterRegistry,
        "eventmesh.state.deliveries",
        "Durable webhook deliveries by current state",
        retryingDeliveries,
        "status",
        "retrying");
    registerGauge(
        meterRegistry,
        "eventmesh.state.deliveries",
        "Durable webhook deliveries by current state",
        succeededDeliveries,
        "status",
        "succeeded");
    registerGauge(
        meterRegistry,
        "eventmesh.state.deliveries",
        "Durable webhook deliveries by current state",
        deadLetterDeliveries,
        "status",
        "dead_letter");
  }

  public void refresh() {
    try {
      var snapshot =
          jdbcTemplate.queryForObject(
              """
              SELECT
                (SELECT COUNT(*) FROM events) AS accepted_events,
                (SELECT COUNT(*) FROM outbox_messages WHERE published_at IS NULL)
                  AS pending_outbox,
                (SELECT COUNT(*) FROM deliveries WHERE status = 'PENDING')
                  AS pending_deliveries,
                (SELECT COUNT(*) FROM deliveries WHERE status = 'RETRYING')
                  AS retrying_deliveries,
                (SELECT COUNT(*) FROM deliveries WHERE status = 'SUCCEEDED')
                  AS succeeded_deliveries,
                (SELECT COUNT(*) FROM deliveries WHERE status = 'DEAD_LETTER')
                  AS dead_letter_deliveries
              """,
              (resultSet, rowNumber) ->
                  new Snapshot(
                      resultSet.getLong("accepted_events"),
                      resultSet.getLong("pending_outbox"),
                      resultSet.getLong("pending_deliveries"),
                      resultSet.getLong("retrying_deliveries"),
                      resultSet.getLong("succeeded_deliveries"),
                      resultSet.getLong("dead_letter_deliveries")));
      if (snapshot != null) {
        acceptedEvents.set(snapshot.acceptedEvents());
        pendingOutbox.set(snapshot.pendingOutbox());
        pendingDeliveries.set(snapshot.pendingDeliveries());
        retryingDeliveries.set(snapshot.retryingDeliveries());
        succeededDeliveries.set(snapshot.succeededDeliveries());
        deadLetterDeliveries.set(snapshot.deadLetterDeliveries());
      }
    } catch (DataAccessException exception) {
      LOGGER.atWarn().setCause(exception).log("Could not refresh durable-state metrics");
    }
  }

  private void registerGauge(
      MeterRegistry registry, String name, String description, AtomicLong value, String... tags) {
    Gauge.builder(name, value, AtomicLong::get)
        .description(description)
        .tags(tags)
        .register(registry);
  }

  private record Snapshot(
      long acceptedEvents,
      long pendingOutbox,
      long pendingDeliveries,
      long retryingDeliveries,
      long succeededDeliveries,
      long deadLetterDeliveries) {}
}
