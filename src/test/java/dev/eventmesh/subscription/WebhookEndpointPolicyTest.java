package dev.eventmesh.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.eventmesh.error.InvalidRequestException;
import dev.eventmesh.webhook.WebhookProperties;
import org.junit.jupiter.api.Test;

class WebhookEndpointPolicyTest {

  @Test
  void publicModeRequiresHttpsAndRejectsPrivateDestinations() {
    var policy = new WebhookEndpointPolicy(new WebhookProperties());

    assertThatThrownBy(() -> policy.requireAllowed("http://example.com/webhooks"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("https");
    assertThatThrownBy(() -> policy.requireAllowed("https://127.0.0.1/webhooks"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("private or local");
  }

  @Test
  void localModePermitsPrivateHttpForTheDemoSubscriber() {
    var properties = new WebhookProperties();
    properties.setAllowPrivateEndpoints(true);
    var policy = new WebhookEndpointPolicy(properties);

    assertThat(policy.requireAllowed("http://127.0.0.1:8090/webhooks").toString())
        .isEqualTo("http://127.0.0.1:8090/webhooks");
  }
}
