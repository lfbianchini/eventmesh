package dev.eventmesh.publication;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
    UUID id,
    UUID tenantId,
    UUID eventId,
    String topic,
    String partitionKey,
    String messageType,
    int schemaVersion,
    String payload,
    Instant createdAt,
    int attemptCount) {}
