package dev.eventmesh.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
    UUID id,
    String type,
    String source,
    String subject,
    JsonNode data,
    Instant receivedAt,
    boolean replayed) {

  static EventResponse from(StoredEvent event, boolean replayed) {
    return new EventResponse(
        event.id(),
        event.type(),
        event.source(),
        event.subject(),
        event.data(),
        event.receivedAt(),
        replayed);
  }
}

