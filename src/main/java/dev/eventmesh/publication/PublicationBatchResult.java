package dev.eventmesh.publication;

public record PublicationBatchResult(int claimed, int published, int failed) {}
