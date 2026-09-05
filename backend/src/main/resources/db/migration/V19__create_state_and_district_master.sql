-- Idempotent (IF NOT EXISTS / DO-EXCEPTION guards): these tables were already
-- created directly against the shared dev database by other tooling before
-- this migration existed here, using UUID primary keys and no soft-delete
-- columns (is_active only, no updated_at/deleted_at) - this migration
-- reproduces that exact shape so it's a no-op on that database and a real
-- create on a fresh one.
CREATE TABLE IF NOT EXISTS state_master (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_code  VARCHAR(10)  NOT NULL,
    state_name  VARCHAR(100) NOT NULL,
    state_type  VARCHAR(50)  NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_state_master_state_type CHECK (state_type IN ('State', 'Union Territory'))
);

DO $$ BEGIN
    ALTER TABLE state_master ADD CONSTRAINT state_master_state_code_key UNIQUE (state_code);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS district_master (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_id       UUID         NOT NULL REFERENCES state_master (id) ON DELETE RESTRICT,
    district_code  VARCHAR(10)  NOT NULL,
    district_name  VARCHAR(100) NOT NULL,
    is_active      BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

DO $$ BEGIN
    ALTER TABLE district_master ADD CONSTRAINT uq_state_district_code UNIQUE (state_id, district_code);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;

DO $$ BEGIN
    ALTER TABLE district_master ADD CONSTRAINT uq_state_district_name UNIQUE (state_id, district_name);
EXCEPTION WHEN duplicate_object OR duplicate_table THEN NULL;
END $$;
