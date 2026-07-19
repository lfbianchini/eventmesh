package dev.eventmesh.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.eventmesh.security.SecretCipher;
import dev.eventmesh.subscription.WebhookEndpointPolicy;
import dev.eventmesh.webhook.WebhookProperties;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WebhookHttpSender {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final SecretCipher secretCipher;
  private final WebhookSigner signer;
  private final WebhookEndpointPolicy endpointPolicy;
  private final WebhookProperties properties;
  private final Clock clock;

  public WebhookHttpSender(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      SecretCipher secretCipher,
      WebhookSigner signer,
      WebhookEndpointPolicy endpointPolicy,
      WebhookProperties properties,
      Clock clock) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.secretCipher = secretCipher;
    this.signer = signer;
    this.endpointPolicy = endpointPolicy;
    this.properties = properties;
    this.clock = clock;
  }

  public int send(DeliveryWork work) throws Exception {
    var endpoint = endpointPolicy.requireAllowed(work.endpointUrl());
    var body = serializeBody(work);
    var timestamp = Instant.now(clock).getEpochSecond();
    var signature =
        signer.sign(
            secretCipher.decrypt(work.signingSecretCiphertext()), timestamp, work.id(), body);
    var request =
        HttpRequest.newBuilder(endpoint)
            .timeout(properties.getRequestTimeout())
            .header("Content-Type", "application/json")
            .header("User-Agent", "EventMesh/1.0")
            .header("X-EventMesh-Delivery", work.id().toString())
            .header("X-EventMesh-Timestamp", Long.toString(timestamp))
            .header("X-EventMesh-Signature", signature)
            .header("X-EventMesh-Attempt", Integer.toString(work.attemptCount() + 1))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  private String serializeBody(DeliveryWork work) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("id", work.eventId());
      body.put("type", work.eventType());
      body.put("source", work.eventSource());
      body.put("subject", work.subject());
      body.put("data", objectMapper.readTree(work.eventData()));
      body.put("receivedAt", work.receivedAt());
      return objectMapper.writeValueAsString(body);
    } catch (Exception exception) {
      throw new IllegalStateException("Webhook body serialization failed", exception);
    }
  }
}
