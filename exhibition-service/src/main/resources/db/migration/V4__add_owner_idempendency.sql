CREATE TABLE owner_idempotency (
    principal_id VARCHAR(200) NOT NULL,
    operation VARCHAR(80) NOT NULL,
    operation_key UUID NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    owner_id UUID,
    response_name VARCHAR(200),
    response_age INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (clock_timestamp() + INTERVAL '24 hours'),
    PRIMARY KEY (principal_id, operation, operation_key),
    CHECK ((state = 'IN_PROGRESS' AND owner_id IS NULL AND response_name IS NULL AND response_age IS NULL)
        OR (state = 'COMPLETED' AND owner_id IS NOT NULL AND response_name IS NOT NULL AND response_age IS NOT NULL))
);

CREATE INDEX idx_owner_idempotency_expiry ON owner_idempotency(expires_at);