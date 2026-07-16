package dev.eventmesh.subscription;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoredSubscription(
    UUID id,
    UUID tenantId,
    String name,
    String endpointUrl,
    List<String> eventTypes,
    String signingSecretCiphertext,
    boolean enabled,
    Instant createdAt) {}
