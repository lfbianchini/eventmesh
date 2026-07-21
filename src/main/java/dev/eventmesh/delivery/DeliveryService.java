package dev.eventmesh.delivery;

import dev.eventmesh.auth.TenantPrincipal;
import dev.eventmesh.error.DeliveryNotFoundException;
import dev.eventmesh.error.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

  private final DeliveryRepository repository;
  private final Clock clock;

  public DeliveryService(DeliveryRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<DeliverySummary> list(TenantPrincipal tenant) {
    return repository.findAllByTenant(tenant.id());
  }

  @Transactional(readOnly = true)
  public DeliveryDetails get(TenantPrincipal tenant, UUID deliveryId) {
    var delivery = requireDelivery(tenant, deliveryId);
    return new DeliveryDetails(delivery, repository.findAttempts(deliveryId));
  }

  @Transactional
  public DeliverySummary replay(TenantPrincipal tenant, UUID deliveryId) {
    var original = requireDelivery(tenant, deliveryId);
    if (original.status() != DeliveryStatus.DEAD_LETTER) {
      throw new InvalidRequestException("Only dead-lettered deliveries can be replayed");
    }
    return repository.insertReplay(original, UUID.randomUUID(), Instant.now(clock));
  }

  private DeliverySummary requireDelivery(TenantPrincipal tenant, UUID deliveryId) {
    return repository
        .findByIdAndTenant(deliveryId, tenant.id())
        .orElseThrow(() -> new DeliveryNotFoundException("The requested delivery was not found"));
  }
}
