package dev.eventmesh.subscription;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubscriptionResponse(
    UUID id,
    String name,
    String endpointUrl,
    List<String> eventTypes,
    boolean enabled,
    Instant createdAt,
    String signingSecret) {

  static SubscriptionResponse from(StoredSubscription subscription, String signingSecret) {
    return new SubscriptionResponse(
        subscription.id(),
        subscription.name(),
        subscription.endpointUrl(),
        subscription.eventTypes(),
        subscription.enabled(),
        subscription.createdAt(),
        signingSecret);
  }
}
