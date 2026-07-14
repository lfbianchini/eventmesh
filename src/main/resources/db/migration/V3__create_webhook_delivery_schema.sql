CREATE TABLE subscriptions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  name VARCHAR(128) NOT NULL,
  endpoint_url VARCHAR(2048) NOT NULL,
  event_types JSONB NOT NULL,
  signing_secret_ciphertext TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT subscriptions_tenant_name_unique UNIQUE (tenant_id, name),
  CONSTRAINT subscriptions_event_types_array
    CHECK (jsonb_typeof(event_types) = 'array')
);

CREATE INDEX subscriptions_tenant_enabled_idx
  ON subscriptions (tenant_id, enabled);

CREATE TABLE deliveries (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  source_message_id UUID NOT NULL,
  event_id UUID NOT NULL REFERENCES events(id),
  subscription_id UUID NOT NULL REFERENCES subscriptions(id),
  replay_of_delivery_id UUID REFERENCES deliveries(id),
  status VARCHAR(32) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  claimed_by UUID,
  claimed_until TIMESTAMPTZ,
  last_error VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  CONSTRAINT deliveries_status_valid
    CHECK (status IN ('PENDING', 'RETRYING', 'SUCCEEDED', 'DEAD_LETTER')),
  CONSTRAINT deliveries_attempt_count_nonnegative CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX deliveries_initial_message_subscription_unique
  ON deliveries (source_message_id, subscription_id)
  WHERE replay_of_delivery_id IS NULL;

CREATE INDEX deliveries_due_idx
  ON deliveries (next_attempt_at, created_at)
  WHERE status IN ('PENDING', 'RETRYING');

CREATE INDEX deliveries_tenant_created_idx
  ON deliveries (tenant_id, created_at DESC);

CREATE TABLE delivery_attempts (
  id UUID PRIMARY KEY,
  delivery_id UUID NOT NULL REFERENCES deliveries(id),
  attempt_number INTEGER NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  response_status INTEGER,
  error_message VARCHAR(1000),
  CONSTRAINT delivery_attempts_delivery_number_unique
    UNIQUE (delivery_id, attempt_number),
  CONSTRAINT delivery_attempts_outcome_valid
    CHECK (outcome IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE')),
  CONSTRAINT delivery_attempts_number_positive CHECK (attempt_number > 0)
);

CREATE INDEX delivery_attempts_delivery_started_idx
  ON delivery_attempts (delivery_id, started_at);
