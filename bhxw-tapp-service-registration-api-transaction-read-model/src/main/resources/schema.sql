CREATE SCHEMA IF NOT EXISTS transaction_read_model;

CREATE TABLE IF NOT EXISTS transaction_read_model.transaction_projection (
    document_id TEXT PRIMARY KEY,
    transaction_id TEXT NOT NULL,
    req_msg_id TEXT NOT NULL,
    transaction_status TEXT NOT NULL,
    callback_status TEXT NOT NULL,
    notification_status TEXT NOT NULL,
    log_audit_status TEXT NOT NULL,
    amount NUMERIC(24, 6) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    account_reference TEXT,
    masked_account TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    last_event_id TEXT NOT NULL,
    last_event_type TEXT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    last_event_occurred_at TIMESTAMPTZ NOT NULL,
    projection_payload JSONB NOT NULL
      CHECK (jsonb_typeof(projection_payload) = 'object'),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    CONSTRAINT uk_transaction_projection_business
      UNIQUE (transaction_id, req_msg_id)
);

CREATE INDEX IF NOT EXISTS idx_transaction_projection_expires
  ON transaction_read_model.transaction_projection (expires_at)
  WHERE expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_projection_status
  ON transaction_read_model.transaction_projection (transaction_status, updated_at DESC);
