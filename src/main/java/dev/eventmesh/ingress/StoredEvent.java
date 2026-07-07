package dev.eventmesh.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record StoredEvent(
    UUID id,
    UUID tenantId,
    String idempotencyKey,
    String type,
    String source,
    String subject,
    JsonNode data,
    String payloadHash,
    Instant receivedAt) {}

