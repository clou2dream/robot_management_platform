CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS operators (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES tenants(id),
    username                 TEXT NOT NULL,
    password_hash            TEXT NOT NULL,
    role                     TEXT NOT NULL CHECK (role IN ('admin','operator','viewer')),
    password_reset_required  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, username)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_operators_username ON operators (username);

CREATE TABLE IF NOT EXISTS robots (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    external_robot_id  TEXT NOT NULL,
    serial_number      TEXT NOT NULL,
    manufacturer       TEXT,
    source_app_id      TEXT,
    status             TEXT NOT NULL DEFAULT 'active'
                       CHECK (status IN ('active','revoked','disabled')),
    synced_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, external_robot_id),
    UNIQUE (tenant_id, serial_number)
);

CREATE INDEX IF NOT EXISTS idx_robots_tenant_status ON robots (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_robots_serial_number ON robots (serial_number);

CREATE TABLE IF NOT EXISTS operator_robot_access (
    operator_id UUID NOT NULL REFERENCES operators(id) ON DELETE CASCADE,
    robot_id    UUID NOT NULL REFERENCES robots(id) ON DELETE CASCADE,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (operator_id, robot_id)
);

CREATE TABLE IF NOT EXISTS alerts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    robot_id      UUID REFERENCES robots(id),
    triggered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at   TIMESTAMPTZ,
    level         TEXT NOT NULL CHECK (level IN ('WARNING','URGENT','CRITICAL','FATAL')),
    error_type    TEXT NOT NULL,
    description   TEXT,
    hint          TEXT,
    raw_error     JSONB
);

CREATE TABLE IF NOT EXISTS orders (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    robot_id          UUID NOT NULL REFERENCES robots(id),
    order_id          TEXT NOT NULL,
    status            TEXT NOT NULL CHECK (status IN ('pending','active','completed','failed','cancelled')),
    payload           JSONB NOT NULL,
    created_by        UUID REFERENCES operators(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (robot_id, order_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_robot_status ON orders (robot_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS robot_connections (
    time              TIMESTAMPTZ NOT NULL,
    tenant_id          UUID NOT NULL REFERENCES tenants(id),
    robot_id           UUID NOT NULL REFERENCES robots(id),
    connection_state   TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_robot_connections_robot_time ON robot_connections (robot_id, time DESC);

CREATE TABLE IF NOT EXISTS robot_states (
    time           TIMESTAMPTZ NOT NULL,
    tenant_id      UUID NOT NULL REFERENCES tenants(id),
    robot_id       UUID NOT NULL REFERENCES robots(id),
    pos_x          DOUBLE PRECISION,
    pos_y          DOUBLE PRECISION,
    pos_theta      DOUBLE PRECISION,
    map_id         TEXT,
    battery_soc    SMALLINT,
    operating_mode TEXT,
    order_id       TEXT,
    raw_payload    JSONB
);

CREATE INDEX IF NOT EXISTS idx_robot_states_robot_time ON robot_states (robot_id, time DESC);

CREATE TABLE IF NOT EXISTS robot_positions (
    time       TIMESTAMPTZ NOT NULL,
    tenant_id  UUID NOT NULL REFERENCES tenants(id),
    robot_id   UUID NOT NULL REFERENCES robots(id),
    pos_x      DOUBLE PRECISION,
    pos_y      DOUBLE PRECISION,
    pos_theta  DOUBLE PRECISION,
    map_id     TEXT
);

CREATE INDEX IF NOT EXISTS idx_robot_positions_robot_time ON robot_positions (robot_id, time DESC);
