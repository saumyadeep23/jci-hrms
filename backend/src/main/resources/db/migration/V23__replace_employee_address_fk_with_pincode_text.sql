-- FR-EMP.14: employee present/permanent state & district are now derived from
-- the India Post pincode lookup (free-text State/District names), not the
-- StateMaster/DistrictMaster admin masters, so the FK columns become plain
-- text columns. Existing values are backfilled by name before the FK columns
-- (and their constraints) are dropped.
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_state VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_district VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_state VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_district VARCHAR(100);

UPDATE employees e
SET present_state = sm.state_name
FROM state_master sm
WHERE e.present_state_id = sm.id AND e.present_state IS NULL;

UPDATE employees e
SET present_district = dm.district_name
FROM district_master dm
WHERE e.present_district_id = dm.id AND e.present_district IS NULL;

UPDATE employees e
SET permanent_state = sm.state_name
FROM state_master sm
WHERE e.permanent_state_id = sm.id AND e.permanent_state IS NULL;

UPDATE employees e
SET permanent_district = dm.district_name
FROM district_master dm
WHERE e.permanent_district_id = dm.id AND e.permanent_district IS NULL;

ALTER TABLE employees DROP COLUMN IF EXISTS present_state_id;
ALTER TABLE employees DROP COLUMN IF EXISTS present_district_id;
ALTER TABLE employees DROP COLUMN IF EXISTS permanent_state_id;
ALTER TABLE employees DROP COLUMN IF EXISTS permanent_district_id;
