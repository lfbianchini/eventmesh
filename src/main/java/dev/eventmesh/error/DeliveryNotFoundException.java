package dev.eventmesh.error;

public class DeliveryNotFoundException extends RuntimeException {

  public DeliveryNotFoundException(String message) {
    super(message);
  }
}
