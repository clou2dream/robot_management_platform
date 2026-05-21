ALTER TABLE robots ADD COLUMN IF NOT EXISTS external_robot_id TEXT;
ALTER TABLE robots ADD COLUMN IF NOT EXISTS source_app_id TEXT;
ALTER TABLE robots ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
ALTER TABLE robots ADD COLUMN IF NOT EXISTS synced_at TIMESTAMPTZ;
ALTER TABLE robots ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'robots'
          AND column_name = 'app_id'
    ) THEN
        UPDATE robots
        SET source_app_id = app_id
        WHERE source_app_id IS NULL;
    END IF;
END $$;

UPDATE robots
SET external_robot_id = COALESCE(NULLIF(external_robot_id, ''), serial_number, id::text)
WHERE external_robot_id IS NULL OR external_robot_id = '';

UPDATE robots
SET created_at = now()
WHERE created_at IS NULL;

UPDATE robots
SET synced_at = COALESCE(synced_at, created_at, now())
WHERE synced_at IS NULL;

UPDATE robots
SET updated_at = COALESCE(updated_at, synced_at, created_at, now())
WHERE updated_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_robots_external_robot_id ON robots (external_robot_id);
