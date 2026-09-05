-- Shift Master (Master Data Console) - named shift definitions for Watchmen/
-- Security/General rosters, distinct from office_timing_config (V36): that
-- table is a single global grace-window config for regular-staff attendance
-- evaluation, not a multi-shift roster - it has no shift_code/name concept
-- and can't represent an overnight shift.
CREATE TABLE shift_master (
    id                    BIGSERIAL PRIMARY KEY,
    shift_code            VARCHAR(20)  NOT NULL,
    shift_name            VARCHAR(100) NOT NULL,
    start_time            TIME         NOT NULL,
    end_time              TIME         NOT NULL,
    grace_period_minutes  INTEGER      NOT NULL DEFAULT 0,
    crosses_midnight      BOOLEAN      NOT NULL DEFAULT false,
    is_active             BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_shift_master_shift_code_active ON shift_master (shift_code) WHERE deleted_at IS NULL;

-- The four standard shifts named in the Master Data Console spec.
INSERT INTO shift_master (shift_code, shift_name, start_time, end_time, grace_period_minutes, crosses_midnight) VALUES
    ('GEN',     'General Shift',       '09:45', '18:15', 15, false),
    ('SHIFT_A', 'Shift A (Morning)',   '06:00', '14:00', 10, false),
    ('SHIFT_B', 'Shift B (Afternoon)', '14:00', '22:00', 10, false),
    ('SHIFT_C', 'Shift C (Night)',     '22:00', '06:00', 10, true);
