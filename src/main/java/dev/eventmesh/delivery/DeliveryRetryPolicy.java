package dev.eventmesh.delivery;

import dev.eventmesh.webhook.WebhookProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryPolicy {

  private final WebhookProperties properties;

  public DeliveryRetryPolicy(WebhookProperties properties) {
    this.properties = properties;
  }

  public Duration delay(UUID deliveryId, int failedAttempt) {
    var exponent = Math.min(Math.max(failedAttempt - 1, 0), 30);
    var baseMillis = properties.getRetryBaseDelay().toMillis();
    var maxMillis = properties.getRetryMaxDelay().toMillis();
    long uncapped;
    try {
      uncapped = Math.multiplyExact(baseMillis, 1L << exponent);
    } catch (ArithmeticException exception) {
      uncapped = maxMillis;
    }
    var capped = Math.min(uncapped, maxMillis);
    var mixed =
        deliveryId.getMostSignificantBits()
            ^ Long.rotateLeft(deliveryId.getLeastSignificantBits(), 17)
            ^ ((long) failedAttempt * 0x9E3779B97F4A7C15L);
    var jitterPermille = 500 + Math.floorMod(mixed, 501);
    return Duration.ofMillis(Math.max(1, capped * jitterPermille / 1000));
  }

  public boolean isRetryableStatus(int status) {
    return status == 408 || status == 429 || status >= 500;
  }
}
