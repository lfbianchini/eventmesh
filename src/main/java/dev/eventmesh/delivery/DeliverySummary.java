package dev.eventmesh.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliverySummary(
    UUID id,
    UUID eventId,
    UUID subscriptionId,
    UUID replayOfDeliveryId,
    DeliveryStatus status,
    int attemptCount,
    Instant nextAttemptAt,
    String lastError,
    Instant createdAt,
    Instant completedAt) {}
