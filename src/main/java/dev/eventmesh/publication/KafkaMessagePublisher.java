package dev.eventmesh.publication;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaMessagePublisher implements MessagePublisher {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public KafkaMessagePublisher(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @Override
  public void publish(OutboxMessage message, Duration timeout) throws Exception {
    kafkaTemplate
        .send(message.topic(), message.partitionKey(), message.payload())
        .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
  }
}
