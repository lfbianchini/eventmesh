package dev.eventmesh.error;

public class SubscriptionConflictException extends RuntimeException {

  public SubscriptionConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
