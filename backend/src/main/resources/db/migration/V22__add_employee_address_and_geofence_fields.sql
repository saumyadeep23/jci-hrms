ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_address_line VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_city VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_state_id UUID REFERENCES state_master (id);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_district_id UUID REFERENCES district_master (id);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS present_pin_code VARCHAR(10);

ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_address_line VARCHAR(255);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_city VARCHAR(100);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_state_id UUID REFERENCES state_master (id);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_district_id UUID REFERENCES district_master (id);
ALTER TABLE employees ADD COLUMN IF NOT EXISTS permanent_pin_code VARCHAR(10);

ALTER TABLE employees ADD COLUMN IF NOT EXISTS is_geofence_exempted BOOLEAN NOT NULL DEFAULT false;
