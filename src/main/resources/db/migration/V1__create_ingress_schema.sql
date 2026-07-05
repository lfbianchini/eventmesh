CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  slug VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tenant_api_keys (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  key_hash CHAR(64) NOT NULL UNIQUE,
  label VARCHAR(128) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX tenant_api_keys_active_hash_idx
  ON tenant_api_keys (key_hash)
  WHERE revoked_at IS NULL;

CREATE TABLE events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  idempotency_key VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_source VARCHAR(256) NOT NULL,
  subject VARCHAR(512),
  data JSONB NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT events_tenant_idempotency_unique
    UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX events_tenant_received_at_idx
  ON events (tenant_id, received_at DESC);

