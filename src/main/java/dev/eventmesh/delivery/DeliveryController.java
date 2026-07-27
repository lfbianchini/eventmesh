package dev.eventmesh.delivery;

import dev.eventmesh.auth.TenantAuthenticator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/deliveries")
@Tag(name = "Deliveries", description = "Webhook state, immutable attempts, and replay")
public class DeliveryController {

  private static final String API_KEY_HEADER = "X-EventMesh-Key";

  private final TenantAuthenticator tenantAuthenticator;
  private final DeliveryService service;

  public DeliveryController(TenantAuthenticator tenantAuthenticator, DeliveryService service) {
    this.tenantAuthenticator = tenantAuthenticator;
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "List the tenant's latest webhook deliveries")
  List<DeliverySummary> list(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
    return service.list(tenantAuthenticator.requireTenant(apiKey));
  }

  @GetMapping("/{deliveryId}")
  @Operation(summary = "Inspect a delivery and its immutable attempt history")
  DeliveryDetails get(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @PathVariable UUID deliveryId) {
    return service.get(tenantAuthenticator.requireTenant(apiKey), deliveryId);
  }

  @PostMapping("/{deliveryId}/replay")
  @Operation(summary = "Create a linked replay of a dead-letter delivery")
  ResponseEntity<DeliverySummary> replay(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @PathVariable UUID deliveryId) {
    var replayed = service.replay(tenantAuthenticator.requireTenant(apiKey), deliveryId);
    return ResponseEntity.created(URI.create("/v1/deliveries/" + replayed.id())).body(replayed);
  }
}
