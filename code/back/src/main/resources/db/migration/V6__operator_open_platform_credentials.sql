CREATE TABLE IF NOT EXISTS operator_open_platform_credentials (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    operator_id UUID NOT NULL REFERENCES operators(id) ON DELETE CASCADE,
    display_name TEXT,
    app_id      TEXT NOT NULL,
    api_key     TEXT NOT NULL,
    api_secret  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (operator_id, app_id, api_key)
);

CREATE INDEX IF NOT EXISTS idx_operator_open_platform_credentials_operator
    ON operator_open_platform_credentials (operator_id, created_at DESC);
