package dev.eventmesh.delivery;

public record DeliveryBatchResult(int claimed, int succeeded, int retrying, int deadLettered) {}
