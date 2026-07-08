package dev.eventmesh.ingress;

import dev.eventmesh.auth.TenantPrincipal;
import dev.eventmesh.error.EventNotFoundException;
import dev.eventmesh.error.IdempotencyConflictException;
import dev.eventmesh.error.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventIngestionService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

  private final EventIngestionRepository repository;
  private final CanonicalEventHasher eventHasher;
  private final Clock clock;

  public EventIngestionService(
      EventIngestionRepository repository, CanonicalEventHasher eventHasher, Clock clock) {
    this.repository = repository;
    this.eventHasher = eventHasher;
    this.clock = clock;
  }

  @Transactional
  public EventIngestionResult ingest(
      TenantPrincipal tenant, String idempotencyKey, IngestEventRequest request) {
    validateIdempotencyKey(idempotencyKey);
    var payloadHash = eventHasher.hash(request);
    var eventId = UUID.randomUUID();
    var receivedAt = Instant.now(clock);

    var inserted =
        repository.insertIfAbsent(
            eventId, tenant.id(), idempotencyKey, request, payloadHash, receivedAt);
    if (inserted.isPresent()) {
      return new EventIngestionResult(inserted.get(), true);
    }

    var existing =
        repository
            .findByIdempotencyKey(tenant.id(), idempotencyKey)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The idempotency constraint matched but no event could be loaded"));

    if (!existing.payloadHash().equals(payloadHash)) {
      throw new IdempotencyConflictException(
          "This idempotency key was already used for a different event request");
    }

    return new EventIngestionResult(existing, false);
  }

  @Transactional(readOnly = true)
  public StoredEvent get(TenantPrincipal tenant, UUID eventId) {
    return repository
        .findByIdAndTenantId(eventId, tenant.id())
        .orElseThrow(() -> new EventNotFoundException("The requested event was not found"));
  }

  private void validateIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new InvalidRequestException("The Idempotency-Key header is required");
    }
    if (idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new InvalidRequestException(
          "The Idempotency-Key header must not exceed "
              + MAX_IDEMPOTENCY_KEY_LENGTH
              + " characters");
    }
  }
}

