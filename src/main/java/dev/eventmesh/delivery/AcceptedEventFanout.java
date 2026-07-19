package dev.eventmesh.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eventmesh.subscription.SubscriptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AcceptedEventFanout {

  private final ObjectMapper objectMapper;
  private final SubscriptionRepository subscriptionRepository;
  private final DeliveryRepository deliveryRepository;
  private final Clock clock;

  public AcceptedEventFanout(
      ObjectMapper objectMapper,
      SubscriptionRepository subscriptionRepository,
      DeliveryRepository deliveryRepository,
      Clock clock) {
    this.objectMapper = objectMapper;
    this.subscriptionRepository = subscriptionRepository;
    this.deliveryRepository = deliveryRepository;
    this.clock = clock;
  }

  @Transactional
  public int accept(String payload) {
    var envelope = parse(payload);
    var messageId = requiredUuid(envelope, "/messageId");
    var tenantId = requiredUuid(envelope, "/tenantId");
    var eventId = requiredUuid(envelope, "/event/id");
    var eventType = requiredText(envelope, "/event/type");
    if (!"event.accepted".equals(requiredText(envelope, "/messageType"))
        || envelope.path("schemaVersion").asInt() != 1) {
      throw new IllegalArgumentException("Unsupported accepted-event message contract");
    }

    int created = 0;
    var now = Instant.now(clock);
    for (var subscription : subscriptionRepository.findEnabledMatching(tenantId, eventType)) {
      if (deliveryRepository.insertInitial(
          UUID.randomUUID(), tenantId, messageId, eventId, subscription.id(), now)) {
        created++;
      }
    }
    return created;
  }

  private JsonNode parse(String payload) {
    try {
      return objectMapper.readTree(payload);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Accepted-event message is not valid JSON", exception);
    }
  }

  private UUID requiredUuid(JsonNode root, String pointer) {
    try {
      return UUID.fromString(requiredText(root, pointer));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Accepted-event message has an invalid UUID at " + pointer);
    }
  }

  private String requiredText(JsonNode root, String pointer) {
    var node = root.at(pointer);
    if (!node.isTextual() || node.asText().isBlank()) {
      throw new IllegalArgumentException(
          "Accepted-event message is missing required field " + pointer);
    }
    return node.asText();
  }
}
