package dev.eventmesh.publication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "eventmesh.outbox.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledOutboxRelay {

  private final OutboxRelay relay;

  public ScheduledOutboxRelay(OutboxRelay relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${eventmesh.outbox.poll-interval:500ms}")
  public void publish() {
    relay.publishAvailable();
  }
}
