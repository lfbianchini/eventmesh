package dev.eventmesh.subscription;

import dev.eventmesh.error.InvalidRequestException;
import dev.eventmesh.webhook.WebhookProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class WebhookEndpointPolicy {

  private final WebhookProperties properties;

  public WebhookEndpointPolicy(WebhookProperties properties) {
    this.properties = properties;
  }

  public URI requireAllowed(String rawUrl) {
    final URI uri;
    try {
      uri = new URI(rawUrl);
    } catch (URISyntaxException exception) {
      throw new InvalidRequestException("The webhook endpoint URL is invalid");
    }

    var scheme = uri.getScheme();
    if (scheme == null
        || (!scheme.equalsIgnoreCase("https")
            && !(properties.isAllowPrivateEndpoints() && scheme.equalsIgnoreCase("http")))) {
      throw new InvalidRequestException(
          properties.isAllowPrivateEndpoints()
              ? "The webhook endpoint must use http or https"
              : "The webhook endpoint must use https");
    }
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getPort() == 0) {
      throw new InvalidRequestException("The webhook endpoint URL is invalid");
    }

    if (!properties.isAllowPrivateEndpoints()) {
      rejectPrivateAddresses(uri.getHost());
    }
    return uri;
  }

  private void rejectPrivateAddresses(String host) {
    try {
      for (var address : InetAddress.getAllByName(host)) {
        if (isPrivate(address)) {
          throw new InvalidRequestException(
              "The webhook endpoint must not resolve to a private or local address");
        }
      }
    } catch (UnknownHostException exception) {
      throw new InvalidRequestException("The webhook endpoint host could not be resolved");
    }
  }

  private boolean isPrivate(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    var bytes = address.getAddress();
    return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
  }
}
