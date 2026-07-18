package dev.eventmesh.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eventmesh.webhook.WebhookProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryRetryPolicyTest {

  @Test
  void appliesDeterministicJitterAndCapsBackoff() {
    var properties = new WebhookProperties();
    properties.setRetryBaseDelay(Duration.ofSeconds(2));
    properties.setRetryMaxDelay(Duration.ofSeconds(10));
    var policy = new DeliveryRetryPolicy(properties);
    var deliveryId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    assertThat(policy.delay(deliveryId, 1)).isEqualTo(policy.delay(deliveryId, 1));
    assertThat(policy.delay(deliveryId, 1)).isBetween(Duration.ofSeconds(1), Duration.ofSeconds(3));
    assertThat(policy.delay(deliveryId, 20))
        .isBetween(Duration.ofSeconds(5), Duration.ofSeconds(10));
  }

  @Test
  void classifiesOnlyTransientHttpStatusesForRetry() {
    var policy = new DeliveryRetryPolicy(new WebhookProperties());

    assertThat(policy.isRetryableStatus(408)).isTrue();
    assertThat(policy.isRetryableStatus(429)).isTrue();
    assertThat(policy.isRetryableStatus(503)).isTrue();
    assertThat(policy.isRetryableStatus(400)).isFalse();
    assertThat(policy.isRetryableStatus(301)).isFalse();
  }
}
