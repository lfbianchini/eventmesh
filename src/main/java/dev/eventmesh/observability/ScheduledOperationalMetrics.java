package dev.eventmesh.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "eventmesh.observability.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledOperationalMetrics {

  private final OperationalMetrics metrics;

  public ScheduledOperationalMetrics(OperationalMetrics metrics) {
    this.metrics = metrics;
  }

  @Scheduled(
      initialDelayString = "${eventmesh.observability.snapshot-interval:5s}",
      fixedDelayString = "${eventmesh.observability.snapshot-interval:5s}")
  public void refresh() {
    metrics.refresh();
  }
}
