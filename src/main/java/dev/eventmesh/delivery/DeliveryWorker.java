package dev.eventmesh.delivery;

import dev.eventmesh.webhook.WebhookProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeliveryWorker {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryWorker.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final UUID workerId = UUID.randomUUID();
  private final DeliveryRepository repository;
  private final WebhookHttpSender sender;
  private final DeliveryRetryPolicy retryPolicy;
  private final WebhookProperties properties;
  private final Clock clock;
  private final Counter succeededCounter;
  private final Counter retryingCounter;
  private final Counter deadLetterCounter;

  public DeliveryWorker(
      DeliveryRepository repository,
      WebhookHttpSender sender,
      DeliveryRetryPolicy retryPolicy,
      WebhookProperties properties,
      Clock clock,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.sender = sender;
    this.retryPolicy = retryPolicy;
    this.properties = properties;
    this.clock = clock;
    this.succeededCounter =
        meterRegistry.counter("eventmesh.webhook.delivery", "outcome", "succeeded");
    this.retryingCounter =
        meterRegistry.counter("eventmesh.webhook.delivery", "outcome", "retrying");
    this.deadLetterCounter =
        meterRegistry.counter("eventmesh.webhook.delivery", "outcome", "dead_letter");
  }

  public DeliveryBatchResult deliverAvailable() {
    var claimedAt = Instant.now(clock);
    var workItems =
        repository.claimBatch(
            workerId,
            claimedAt,
            claimedAt.plus(properties.getLeaseDuration()),
            properties.getBatchSize());
    int succeeded = 0;
    int retrying = 0;
    int deadLettered = 0;

    for (var work : workItems) {
      var startedAt = Instant.now(clock);
      try {
        var responseStatus = sender.send(work);
        var finishedAt = Instant.now(clock);
        if (responseStatus >= 200 && responseStatus < 300) {
          if (repository.recordSuccess(work, workerId, startedAt, finishedAt, responseStatus)) {
            succeeded++;
            succeededCounter.increment();
            logOutcome(work, "succeeded", responseStatus);
          }
        } else {
          var retryable = retryPolicy.isRetryableStatus(responseStatus);
          var terminal = !retryable || work.attemptCount() + 1 >= properties.getMaxAttempts();
          var error = "Subscriber returned HTTP " + responseStatus;
          var recorded =
              repository.recordFailure(
                  work,
                  workerId,
                  startedAt,
                  finishedAt,
                  retryable ? "RETRYABLE_FAILURE" : "PERMANENT_FAILURE",
                  responseStatus,
                  error,
                  terminal,
                  terminal
                      ? finishedAt
                      : finishedAt.plus(retryPolicy.delay(work.id(), work.attemptCount() + 1)));
          if (recorded) {
            if (terminal) {
              deadLettered++;
              deadLetterCounter.increment();
              logOutcome(work, "dead_letter", responseStatus);
            } else {
              retrying++;
              retryingCounter.increment();
              logOutcome(work, "retrying", responseStatus);
            }
          }
        }
      } catch (Exception exception) {
        if (exception instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        var finishedAt = Instant.now(clock);
        var terminal = work.attemptCount() + 1 >= properties.getMaxAttempts();
        var error = safeError(exception);
        var recorded =
            repository.recordFailure(
                work,
                workerId,
                startedAt,
                finishedAt,
                "RETRYABLE_FAILURE",
                null,
                error,
                terminal,
                terminal
                    ? finishedAt
                    : finishedAt.plus(retryPolicy.delay(work.id(), work.attemptCount() + 1)));
        if (recorded) {
          if (terminal) {
            deadLettered++;
            deadLetterCounter.increment();
          } else {
            retrying++;
            retryingCounter.increment();
          }
        }
        LOGGER
            .atWarn()
            .addKeyValue("deliveryId", work.id())
            .addKeyValue("eventId", work.eventId())
            .addKeyValue("attempt", work.attemptCount() + 1)
            .setCause(exception)
            .log("Webhook delivery failed");
      }
    }
    return new DeliveryBatchResult(workItems.size(), succeeded, retrying, deadLettered);
  }

  private void logOutcome(DeliveryWork work, String outcome, int responseStatus) {
    LOGGER
        .atInfo()
        .addKeyValue("deliveryId", work.id())
        .addKeyValue("eventId", work.eventId())
        .addKeyValue("attempt", work.attemptCount() + 1)
        .addKeyValue("responseStatus", responseStatus)
        .addKeyValue("outcome", outcome)
        .log("Webhook delivery completed");
  }

  private String safeError(Exception exception) {
    var message =
        exception.getMessage() == null
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + exception.getMessage();
    return message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
  }
}
