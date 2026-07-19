package dev.eventmesh.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

  @Test
  void signsTimestampDeliveryIdentityAndExactBody() {
    var signer = new WebhookSigner();
    var deliveryId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    var secret = "subscriber-secret".getBytes(StandardCharsets.UTF_8);

    var signature = signer.sign(secret, 1_700_000_000L, deliveryId, "{\"ok\":true}");

    assertThat(signature)
        .isEqualTo("v1=6d44b392d7f4139d1223c608e0d1a5546eab1324851c2f78c2d9b8181520b401");
    assertThat(signer.sign(secret, 1_700_000_000L, deliveryId, "{\"ok\":false}"))
        .isNotEqualTo(signature);
  }
}
