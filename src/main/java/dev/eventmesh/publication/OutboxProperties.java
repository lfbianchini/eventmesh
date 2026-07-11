package dev.eventmesh.publication;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventmesh.outbox")
public class OutboxProperties {

  private boolean schedulingEnabled = true;
  private int batchSize = 50;
  private Duration pollInterval = Duration.ofMillis(500);
  private Duration leaseDuration = Duration.ofSeconds(30);
  private Duration sendTimeout = Duration.ofSeconds(10);
  private Duration retryBaseDelay = Duration.ofSeconds(1);
  private Duration retryMaxDelay = Duration.ofMinutes(1);

  public boolean isSchedulingEnabled() {
    return schedulingEnabled;
  }

  public void setSchedulingEnabled(boolean schedulingEnabled) {
    this.schedulingEnabled = schedulingEnabled;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public Duration getPollInterval() {
    return pollInterval;
  }

  public void setPollInterval(Duration pollInterval) {
    this.pollInterval = pollInterval;
  }

  public Duration getLeaseDuration() {
    return leaseDuration;
  }

  public void setLeaseDuration(Duration leaseDuration) {
    this.leaseDuration = leaseDuration;
  }

  public Duration getSendTimeout() {
    return sendTimeout;
  }

  public void setSendTimeout(Duration sendTimeout) {
    this.sendTimeout = sendTimeout;
  }

  public Duration getRetryBaseDelay() {
    return retryBaseDelay;
  }

  public void setRetryBaseDelay(Duration retryBaseDelay) {
    this.retryBaseDelay = retryBaseDelay;
  }

  public Duration getRetryMaxDelay() {
    return retryMaxDelay;
  }

  public void setRetryMaxDelay(Duration retryMaxDelay) {
    this.retryMaxDelay = retryMaxDelay;
  }
}
