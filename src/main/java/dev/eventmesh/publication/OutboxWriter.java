package dev.eventmesh.publication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eventmesh.ingress.StoredEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxWriter {

  static final String MESSAGE_TYPE = "event.accepted";
  static final int SCHEMA_VERSION = 1;

  private final OutboxRepository repository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public void enqueueAccepted(StoredEvent event) {
    var messageId = UUID.randomUUID();
    var createdAt = Instant.now(clock);
    repository.insert(
        messageId,
        event.tenantId(),
        event.id(),
        PublicationConfiguration.ACCEPTED_EVENTS_TOPIC,
        event.id().toString(),
        MESSAGE_TYPE,
        SCHEMA_VERSION,
        serializeEnvelope(messageId, event),
        createdAt);
  }

  private String serializeEnvelope(UUID messageId, StoredEvent event) {
    Map<String, Object> eventBody = new LinkedHashMap<>();
    eventBody.put("id", event.id());
    eventBody.put("type", event.type());
    eventBody.put("source", event.source());
    eventBody.put("subject", event.subject());
    eventBody.put("data", event.data());
    eventBody.put("receivedAt", event.receivedAt());

    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("specVersion", "1.0");
    envelope.put("messageId", messageId);
    envelope.put("messageType", MESSAGE_TYPE);
    envelope.put("schemaVersion", SCHEMA_VERSION);
    envelope.put("occurredAt", event.receivedAt());
    envelope.put("tenantId", event.tenantId());
    envelope.put("event", eventBody);

    try {
      return objectMapper.writeValueAsString(envelope);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("The accepted event envelope could not be serialized", exception);
    }
  }
}
