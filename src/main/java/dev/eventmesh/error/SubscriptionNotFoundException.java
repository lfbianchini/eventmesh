package dev.eventmesh.error;

public class SubscriptionNotFoundException extends RuntimeException {

  public SubscriptionNotFoundException(String message) {
    super(message);
  }
}
