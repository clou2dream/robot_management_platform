ALTER TABLE orders ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS created_by UUID;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

UPDATE orders o
SET tenant_id = r.tenant_id
FROM robots r
WHERE o.robot_id = r.id
  AND o.tenant_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'orders'
          AND column_name = 'operator_id'
    ) THEN
        UPDATE orders
        SET created_by = operator_id
        WHERE created_by IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'orders'
          AND column_name = 'completed_at'
    ) THEN
        UPDATE orders
        SET updated_at = COALESCE(completed_at, created_at, now())
        WHERE updated_at IS NULL;
    ELSE
        UPDATE orders
        SET updated_at = COALESCE(created_at, now())
        WHERE updated_at IS NULL;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'orders'
          AND column_name = 'order_update_id'
    ) THEN
        ALTER TABLE orders ALTER COLUMN order_update_id SET DEFAULT 0;
    END IF;
END $$;

ALTER TABLE orders ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE robot_connections ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE robot_states ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE robot_positions ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE robot_connections c
SET tenant_id = r.tenant_id
FROM robots r
WHERE c.robot_id = r.id
  AND c.tenant_id IS NULL;

UPDATE robot_states s
SET tenant_id = r.tenant_id
FROM robots r
WHERE s.robot_id = r.id
  AND s.tenant_id IS NULL;

UPDATE robot_positions p
SET tenant_id = r.tenant_id
FROM robots r
WHERE p.robot_id = r.id
  AND p.tenant_id IS NULL;

UPDATE alerts a
SET tenant_id = r.tenant_id
FROM robots r
WHERE a.robot_id = r.id
  AND a.tenant_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_orders_robot_status ON orders (robot_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_robot_connections_robot_time ON robot_connections (robot_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_robot_states_robot_time ON robot_states (robot_id, time DESC);
CREATE INDEX IF NOT EXISTS idx_robot_positions_robot_time ON robot_positions (robot_id, time DESC);
