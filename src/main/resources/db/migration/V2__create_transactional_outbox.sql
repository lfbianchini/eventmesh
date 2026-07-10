CREATE TABLE outbox_messages (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenants(id),
  event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  topic VARCHAR(255) NOT NULL,
  partition_key VARCHAR(255) NOT NULL,
  message_type VARCHAR(128) NOT NULL,
  schema_version INTEGER NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  published_at TIMESTAMPTZ,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error VARCHAR(1000),
  claimed_by UUID,
  claimed_until TIMESTAMPTZ,
  CONSTRAINT outbox_event_message_unique UNIQUE (event_id, message_type),
  CONSTRAINT outbox_schema_version_positive CHECK (schema_version > 0),
  CONSTRAINT outbox_attempt_count_nonnegative CHECK (attempt_count >= 0)
);

CREATE INDEX outbox_pending_idx
  ON outbox_messages (next_attempt_at, created_at)
  WHERE published_at IS NULL;

CREATE INDEX outbox_expired_claim_idx
  ON outbox_messages (claimed_until)
  WHERE published_at IS NULL AND claimed_by IS NOT NULL;
