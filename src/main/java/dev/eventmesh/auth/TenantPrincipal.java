package dev.eventmesh.auth;

import java.util.UUID;

public record TenantPrincipal(UUID id, String slug) {}

