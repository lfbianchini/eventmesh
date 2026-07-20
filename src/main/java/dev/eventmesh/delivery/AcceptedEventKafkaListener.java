package dev.eventmesh.delivery;

import dev.eventmesh.publication.PublicationConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "eventmesh.webhooks.kafka-listener-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AcceptedEventKafkaListener {

  private final AcceptedEventFanout fanout;

  public AcceptedEventKafkaListener(AcceptedEventFanout fanout) {
    this.fanout = fanout;
  }

  @KafkaListener(
      topics = PublicationConfiguration.ACCEPTED_EVENTS_TOPIC,
      groupId = "${eventmesh.webhooks.consumer-group:eventmesh-webhook-delivery-v1}")
  public void onAcceptedEvent(String payload) {
    fanout.accept(payload);
  }
}
