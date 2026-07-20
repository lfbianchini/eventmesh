package dev.eventmesh.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "eventmesh.webhooks.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledDeliveryWorker {

  private final DeliveryWorker worker;

  public ScheduledDeliveryWorker(DeliveryWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${eventmesh.webhooks.poll-interval:500ms}")
  public void deliver() {
    worker.deliverAvailable();
  }
}
