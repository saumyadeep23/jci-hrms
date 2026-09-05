-- Idempotent: ro_master was already renamed/expanded directly against the
-- shared dev database (code -> ro_code, name -> ro_name, plus the columns
-- below) before this migration existed here. Each step is guarded so this
-- is a no-op there and a real change on a fresh database.
DO $$ BEGIN
    ALTER TABLE ro_master RENAME COLUMN code TO ro_code;
EXCEPTION WHEN undefined_column THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE ro_master RENAME COLUMN name TO ro_name;
EXCEPTION WHEN undefined_column THEN NULL;
END $$;

-- V4 created this as uq_ro_master_code_active - keep it in step with the ro_code rename above.
ALTER INDEX IF EXISTS uq_ro_master_code_active RENAME TO uq_ro_master_ro_code_active;

ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS office_type VARCHAR(30) NOT NULL DEFAULT 'REGIONAL_OFFICE';
DO $$ BEGIN
    ALTER TABLE ro_master ADD CONSTRAINT ck_ro_master_office_type
        CHECK (office_type IN ('HEAD_OFFICE', 'REGIONAL_OFFICE', 'WAREHOUSE'));
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS address_line TEXT;
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS district VARCHAR(100);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS district_code VARCHAR(20);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS pin_code VARCHAR(10);

ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS geofence_radius_meters NUMERIC(8, 2) NOT NULL DEFAULT 50.00;

ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS recr_club_deduc NUMERIC(10, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE ro_master ADD COLUMN IF NOT EXISTS is_proc_allow_applicable BOOLEAN NOT NULL DEFAULT true;

DO $$ BEGIN
    ALTER TABLE ro_master ADD CONSTRAINT ck_ro_master_ro_code_format CHECK (ro_code ~ '^[0-9]{2}$');
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE ro_master ADD CONSTRAINT uq_ro_master_ro_code UNIQUE (ro_code);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
