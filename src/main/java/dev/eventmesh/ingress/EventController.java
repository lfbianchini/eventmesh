package dev.eventmesh.ingress;

import dev.eventmesh.auth.TenantAuthenticator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/v1/events")
@Tag(name = "Events", description = "Durable, tenant-scoped event ingestion and lookup")
public class EventController {

  static final String API_KEY_HEADER = "X-EventMesh-Key";
  static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

  private final TenantAuthenticator tenantAuthenticator;
  private final EventIngestionService eventIngestionService;

  public EventController(
      TenantAuthenticator tenantAuthenticator, EventIngestionService eventIngestionService) {
    this.tenantAuthenticator = tenantAuthenticator;
    this.eventIngestionService = eventIngestionService;
  }

  @PostMapping
  @Operation(summary = "Accept an event with tenant-scoped idempotency")
  ResponseEntity<EventResponse> ingest(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody IngestEventRequest request) {
    var tenant = tenantAuthenticator.requireTenant(apiKey);
    var result = eventIngestionService.ingest(tenant, idempotencyKey, request);
    var response = EventResponse.from(result.event(), !result.created());

    if (result.created()) {
      return ResponseEntity.created(URI.create("/v1/events/" + result.event().id())).body(response);
    }
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{eventId}")
  @Operation(summary = "Get an event visible to the authenticated tenant")
  EventResponse get(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
      @PathVariable UUID eventId) {
    var tenant = tenantAuthenticator.requireTenant(apiKey);
    return EventResponse.from(eventIngestionService.get(tenant, eventId), false);
  }
}
