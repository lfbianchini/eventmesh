package dev.eventmesh.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {

  @Test
  void exponentiallyBacksOffAndCapsPublicationRetries() {
    var properties = new OutboxProperties();
    properties.setRetryBaseDelay(Duration.ofSeconds(2));
    properties.setRetryMaxDelay(Duration.ofSeconds(30));
    var relay =
        new OutboxRelay(
            mock(OutboxRepository.class),
            mock(MessagePublisher.class),
            properties,
            Clock.systemUTC(),
            new SimpleMeterRegistry());

    assertThat(relay.retryDelayForAttempt(1)).isEqualTo(Duration.ofSeconds(2));
    assertThat(relay.retryDelayForAttempt(2)).isEqualTo(Duration.ofSeconds(4));
    assertThat(relay.retryDelayForAttempt(4)).isEqualTo(Duration.ofSeconds(16));
    assertThat(relay.retryDelayForAttempt(5)).isEqualTo(Duration.ofSeconds(30));
    assertThat(relay.retryDelayForAttempt(100)).isEqualTo(Duration.ofSeconds(30));
  }
}
