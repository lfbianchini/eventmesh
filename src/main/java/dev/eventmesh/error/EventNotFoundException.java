package dev.eventmesh.error;

public class EventNotFoundException extends RuntimeException {

  public EventNotFoundException(String message) {
    super(message);
  }
}

