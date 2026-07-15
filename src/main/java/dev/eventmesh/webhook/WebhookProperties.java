package dev.eventmesh.webhook;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventmesh.webhooks")
public class WebhookProperties {

  private boolean kafkaListenerEnabled = true;
  private boolean schedulingEnabled = true;
  private boolean allowPrivateEndpoints = false;
  private int batchSize = 25;
  private int maxAttempts = 5;
  private Duration pollInterval = Duration.ofMillis(500);
  private Duration leaseDuration = Duration.ofSeconds(30);
  private Duration connectTimeout = Duration.ofSeconds(3);
  private Duration requestTimeout = Duration.ofSeconds(5);
  private Duration retryBaseDelay = Duration.ofSeconds(1);
  private Duration retryMaxDelay = Duration.ofMinutes(5);

  public boolean isKafkaListenerEnabled() {
    return kafkaListenerEnabled;
  }

  public void setKafkaListenerEnabled(boolean kafkaListenerEnabled) {
    this.kafkaListenerEnabled = kafkaListenerEnabled;
  }

  public boolean isSchedulingEnabled() {
    return schedulingEnabled;
  }

  public void setSchedulingEnabled(boolean schedulingEnabled) {
    this.schedulingEnabled = schedulingEnabled;
  }

  public boolean isAllowPrivateEndpoints() {
    return allowPrivateEndpoints;
  }

  public void setAllowPrivateEndpoints(boolean allowPrivateEndpoints) {
    this.allowPrivateEndpoints = allowPrivateEndpoints;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
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

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public void setRequestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
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
