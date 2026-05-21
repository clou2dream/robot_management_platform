DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'robots'
          AND column_name = 'tenant_app_id'
    ) THEN
        ALTER TABLE robots ALTER COLUMN tenant_app_id DROP NOT NULL;
    END IF;
END $$;

UPDATE robots
SET status = 'active'
WHERE status = 'pending';

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.robots'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE robots DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE robots
    ADD CONSTRAINT robots_status_check
    CHECK (status IN ('active', 'revoked', 'disabled'));
