package dev.eventmesh.publication;

import java.time.Duration;

public interface MessagePublisher {

  void publish(OutboxMessage message, Duration timeout) throws Exception;
}
