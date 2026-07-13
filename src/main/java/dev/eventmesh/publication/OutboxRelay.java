package dev.eventmesh.publication;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {

  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final UUID workerId = UUID.randomUUID();
  private final OutboxRepository repository;
  private final MessagePublisher publisher;
  private final OutboxProperties properties;
  private final Clock clock;
  private final Counter publishedCounter;
  private final Counter failedCounter;
  private final Timer publishTimer;

  public OutboxRelay(
      OutboxRepository repository,
      MessagePublisher publisher,
      OutboxProperties properties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.publisher = publisher;
    this.properties = properties;
    this.clock = clock;
    this.publishedCounter = meterRegistry.counter("eventmesh.outbox.publish", "outcome", "success");
    this.failedCounter = meterRegistry.counter("eventmesh.outbox.publish", "outcome", "failure");
    this.publishTimer = meterRegistry.timer("eventmesh.outbox.publish.duration");
  }

  public PublicationBatchResult publishAvailable() {
    var claimedAt = Instant.now(clock);
    var messages =
        repository.claimBatch(
            workerId,
            claimedAt,
            claimedAt.plus(properties.getLeaseDuration()),
            properties.getBatchSize());
    int published = 0;
    int failed = 0;

    for (var message : messages) {
      try {
        publishTimer.recordCallable(
            () -> {
              publisher.publish(message, properties.getSendTimeout());
              return null;
            });
        var marked = repository.markPublished(message.id(), workerId, Instant.now(clock));
        if (marked) {
          published++;
          publishedCounter.increment();
          LOGGER
              .atInfo()
              .addKeyValue("messageId", message.id())
              .addKeyValue("eventId", message.eventId())
              .addKeyValue("attempt", message.attemptCount() + 1)
              .log("Published outbox message");
        } else {
          LOGGER
              .atWarn()
              .addKeyValue("messageId", message.id())
              .log("Published message but its outbox lease was no longer owned");
        }
      } catch (Exception exception) {
        failed++;
        failedCounter.increment();
        var nextAttempt =
            Instant.now(clock).plus(retryDelayForAttempt(message.attemptCount() + 1));
        repository.markFailed(
            message.id(), workerId, nextAttempt, safeErrorMessage(exception));
        LOGGER
            .atWarn()
            .addKeyValue("messageId", message.id())
            .addKeyValue("eventId", message.eventId())
            .addKeyValue("attempt", message.attemptCount() + 1)
            .addKeyValue("nextAttemptAt", nextAttempt)
            .setCause(exception)
            .log("Outbox publication failed");
      }
    }

    return new PublicationBatchResult(messages.size(), published, failed);
  }

  Duration retryDelayForAttempt(int attempt) {
    long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 30);
    Duration candidate;
    try {
      candidate = properties.getRetryBaseDelay().multipliedBy(multiplier);
    } catch (ArithmeticException exception) {
      return properties.getRetryMaxDelay();
    }
    return candidate.compareTo(properties.getRetryMaxDelay()) > 0
        ? properties.getRetryMaxDelay()
        : candidate;
  }

  private String safeErrorMessage(Exception exception) {
    var message =
        exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + exception.getMessage();
    return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
  }
}
