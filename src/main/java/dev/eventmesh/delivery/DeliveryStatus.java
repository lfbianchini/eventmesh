package dev.eventmesh.delivery;

public enum DeliveryStatus {
  PENDING,
  RETRYING,
  SUCCEEDED,
  DEAD_LETTER
}
