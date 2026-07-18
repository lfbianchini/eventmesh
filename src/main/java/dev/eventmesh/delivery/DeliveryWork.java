package dev.eventmesh.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryWork(
    UUID id,
    UUID tenantId,
    UUID sourceMessageId,
    UUID eventId,
    UUID subscriptionId,
    String endpointUrl,
    String signingSecretCiphertext,
    String eventType,
    String eventSource,
    String subject,
    String eventData,
    Instant receivedAt,
    int attemptCount) {}
