CREATE TABLE IF NOT EXISTS robot_factsheets (
    robot_id     UUID PRIMARY KEY REFERENCES robots(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    raw_payload  JSONB NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_robot_factsheets_tenant_received
    ON robot_factsheets (tenant_id, received_at DESC);

CREATE TABLE IF NOT EXISTS mqtt_unauthorized_messages (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    topic            TEXT NOT NULL,
    manufacturer     TEXT,
    serial_number    TEXT,
    channel          TEXT,
    app_id           TEXT,
    robot_unique_id  TEXT,
    reason           TEXT NOT NULL,
    raw_payload      JSONB
);

CREATE INDEX IF NOT EXISTS idx_mqtt_unauthorized_messages_received
    ON mqtt_unauthorized_messages (received_at DESC);

CREATE INDEX IF NOT EXISTS idx_mqtt_unauthorized_messages_robot
    ON mqtt_unauthorized_messages (manufacturer, serial_number, received_at DESC);

UPDATE operator_open_platform_credentials
SET api_secret = encode(digest(api_secret, 'sha256'), 'hex')
WHERE api_secret IS NOT NULL
  AND api_secret !~ '^[0-9a-f]{64}$';
