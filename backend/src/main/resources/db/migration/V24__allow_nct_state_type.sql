-- Delhi is seeded in state_master with state_type = 'NCT' (National Capital
-- Territory), which V19's CHECK constraint never allowed - harmless on the
-- shared dev DB (V19's CREATE TABLE IF NOT EXISTS skipped the constraint
-- there entirely since the table pre-existed), but a fresh DB running V19
-- from scratch would reject that row. Widen the constraint to match reality.
ALTER TABLE state_master DROP CONSTRAINT IF EXISTS ck_state_master_state_type;
ALTER TABLE state_master ADD CONSTRAINT ck_state_master_state_type CHECK (state_type IN ('State', 'Union Territory', 'NCT'));
