-- Idempotent: dpc_master was already renamed/expanded directly against the
-- shared dev database (code -> dpc_code, name -> dpc_name, ro_id -> ro_code
-- string FK, plus the columns below) before this migration existed here.
DO $$ BEGIN
    ALTER TABLE dpc_master RENAME COLUMN code TO dpc_code;
EXCEPTION WHEN undefined_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE dpc_master RENAME COLUMN name TO dpc_name;
EXCEPTION WHEN undefined_column THEN NULL;
END $$;

-- V4 created this as uq_dpc_master_code_active - keep it in step with the dpc_code rename above.
ALTER INDEX IF EXISTS uq_dpc_master_code_active RENAME TO uq_dpc_master_dpc_code_active;

ALTER TABLE dpc_master ALTER COLUMN latitude TYPE NUMERIC(10, 7);
ALTER TABLE dpc_master ALTER COLUMN longitude TYPE NUMERIC(10, 7);
ALTER TABLE dpc_master ALTER COLUMN geofence_radius_meters SET DEFAULT 100;

ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS address_line TEXT;
ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS pin_code VARCHAR(10);
ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS district_code VARCHAR(20);
ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS dpc_short_name VARCHAR(20);

ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS dpc_type VARCHAR(20) NOT NULL DEFAULT 'DPC';
DO $$ BEGIN
    ALTER TABLE dpc_master ADD CONSTRAINT ck_dpc_master_type CHECK (dpc_type IN ('DPC', 'SUB_DPC'));
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS city_class VARCHAR(1) NOT NULL DEFAULT 'Z';
DO $$ BEGIN
    ALTER TABLE dpc_master ADD CONSTRAINT ck_dpc_master_city_class CHECK (city_class IN ('X', 'Y', 'Z'));
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE dpc_master ADD CONSTRAINT ck_dpc_master_dpc_code_format CHECK (dpc_code ~ '^[0-9]{4}$');
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

-- Replace the relational ro_id FK with the string ro_code FK (already done on the shared dev DB - ro_id no longer exists there).
ALTER TABLE dpc_master ADD COLUMN IF NOT EXISTS ro_code VARCHAR(20);

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'dpc_master' AND column_name = 'ro_id') THEN
        UPDATE dpc_master d SET ro_code = r.ro_code FROM ro_master r WHERE d.ro_id = r.id AND d.ro_code IS NULL;
    END IF;
END $$;

ALTER TABLE dpc_master ALTER COLUMN ro_code SET NOT NULL;

DO $$ BEGIN
    ALTER TABLE dpc_master ADD CONSTRAINT dpc_master_ro_code_fkey
        FOREIGN KEY (ro_code) REFERENCES ro_master (ro_code) ON UPDATE CASCADE ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS ix_dpc_master_ro_code ON dpc_master (ro_code);

ALTER TABLE dpc_master DROP CONSTRAINT IF EXISTS dpc_master_ro_id_fkey;
DROP INDEX IF EXISTS ix_dpc_master_ro_id;
ALTER TABLE dpc_master DROP COLUMN IF EXISTS ro_id;
