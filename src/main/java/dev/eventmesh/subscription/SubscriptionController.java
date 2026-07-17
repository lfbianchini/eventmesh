package dev.eventmesh.subscription;

import dev.eventmesh.auth.TenantAuthenticator;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/subscriptions")
public class SubscriptionController {

  private static final String API_KEY_HEADER = "X-EventMesh-Key";

  private final TenantAuthenticator tenantAuthenticator;
  private final SubscriptionService service;

  public SubscriptionController(
      TenantAuthenticator tenantAuthenticator, SubscriptionService service) {
    this.tenantAuthenticator = tenantAuthenticator;
    this.service = service;
  }

  @PostMapping
  ResponseEntity<SubscriptionResponse> create(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @Valid @RequestBody CreateSubscriptionRequest request) {
    var tenant = tenantAuthenticator.requireTenant(apiKey);
    var created = service.create(tenant, request);
    return ResponseEntity.created(
            URI.create("/v1/subscriptions/" + created.subscription().id()))
        .body(SubscriptionResponse.from(created.subscription(), created.signingSecret()));
  }

  @GetMapping
  List<SubscriptionResponse> list(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
    var tenant = tenantAuthenticator.requireTenant(apiKey);
    return service.list(tenant).stream()
        .map(subscription -> SubscriptionResponse.from(subscription, null))
        .toList();
  }

  @GetMapping("/{subscriptionId}")
  SubscriptionResponse get(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @PathVariable UUID subscriptionId) {
    var tenant = tenantAuthenticator.requireTenant(apiKey);
    return SubscriptionResponse.from(service.get(tenant, subscriptionId), null);
  }
}
