package dev.eventmesh.ingress;

public record EventIngestionResult(StoredEvent event, boolean created) {}

