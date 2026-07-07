package dev.eventmesh.auth;

import dev.eventmesh.error.UnauthorizedException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthenticator {

  private final JdbcClient jdbcClient;
  private final Clock clock;

  public TenantAuthenticator(JdbcClient jdbcClient, Clock clock) {
    this.jdbcClient = jdbcClient;
    this.clock = clock;
  }

  public TenantPrincipal requireTenant(String rawApiKey) {
    if (rawApiKey == null || rawApiKey.isBlank()) {
      throw new UnauthorizedException("A valid EventMesh API key is required");
    }

    return findActiveKey(ApiKeyHasher.sha256(rawApiKey))
        .orElseThrow(() -> new UnauthorizedException("A valid EventMesh API key is required"));
  }

  private Optional<TenantPrincipal> findActiveKey(String keyHash) {
    return jdbcClient
        .sql(
            """
            SELECT t.id, t.slug
            FROM tenant_api_keys AS k
            JOIN tenants AS t ON t.id = k.tenant_id
            WHERE k.key_hash = :keyHash
              AND k.revoked_at IS NULL
              AND (k.expires_at IS NULL OR k.expires_at > :now)
            """)
        .param("keyHash", keyHash)
        .param("now", OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC))
        .query(
            (resultSet, rowNumber) ->
                new TenantPrincipal(
                    resultSet.getObject("id", UUID.class), resultSet.getString("slug")))
        .optional();
  }
}
