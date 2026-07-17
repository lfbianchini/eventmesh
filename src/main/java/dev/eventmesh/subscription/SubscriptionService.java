package dev.eventmesh.subscription;

import dev.eventmesh.auth.TenantPrincipal;
import dev.eventmesh.error.SubscriptionNotFoundException;
import dev.eventmesh.error.SubscriptionConflictException;
import dev.eventmesh.security.SecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {

  private final SubscriptionRepository repository;
  private final WebhookEndpointPolicy endpointPolicy;
  private final SecretCipher secretCipher;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public SubscriptionService(
      SubscriptionRepository repository,
      WebhookEndpointPolicy endpointPolicy,
      SecretCipher secretCipher,
      Clock clock) {
    this.repository = repository;
    this.endpointPolicy = endpointPolicy;
    this.secretCipher = secretCipher;
    this.clock = clock;
  }

  @Transactional
  public CreatedSubscription create(
      TenantPrincipal tenant, CreateSubscriptionRequest request) {
    var endpoint = endpointPolicy.requireAllowed(request.endpointUrl());
    var eventTypes = List.copyOf(new LinkedHashSet<>(request.eventTypes()));
    var signingSecret =
        request.signingSecret() == null || request.signingSecret().isBlank()
            ? generateSigningSecret()
            : request.signingSecret();
    final StoredSubscription stored;
    try {
      stored =
          repository.insert(
              UUID.randomUUID(),
              tenant.id(),
              request.name(),
              endpoint.toASCIIString(),
              eventTypes,
              secretCipher.encrypt(signingSecret.getBytes(StandardCharsets.UTF_8)),
              Instant.now(clock));
    } catch (DataIntegrityViolationException exception) {
      throw new SubscriptionConflictException(
          "A subscription with this name already exists for the tenant", exception);
    }
    return new CreatedSubscription(stored, signingSecret);
  }

  @Transactional(readOnly = true)
  public List<StoredSubscription> list(TenantPrincipal tenant) {
    return repository.findAllByTenant(tenant.id());
  }

  @Transactional(readOnly = true)
  public StoredSubscription get(TenantPrincipal tenant, UUID subscriptionId) {
    return repository
        .findByIdAndTenant(subscriptionId, tenant.id())
        .orElseThrow(
            () -> new SubscriptionNotFoundException("The requested subscription was not found"));
  }

  private String generateSigningSecret() {
    var bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record CreatedSubscription(StoredSubscription subscription, String signingSecret) {}
}
