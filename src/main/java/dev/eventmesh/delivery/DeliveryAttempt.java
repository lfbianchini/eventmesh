package dev.eventmesh.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttempt(
    UUID id,
    int attemptNumber,
    Instant startedAt,
    Instant finishedAt,
    String outcome,
    Integer responseStatus,
    String errorMessage) {}
