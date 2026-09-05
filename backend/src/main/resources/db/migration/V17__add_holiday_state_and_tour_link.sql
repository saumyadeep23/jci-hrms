-- Location-aware holidays: NULL state means the holiday applies nationally
-- (GAZETTED) or to every location (RESTRICTED); a non-null state scopes it
-- to employees posted in that state (resolved via their RO/DPC, or a fixed
-- HO state on the application side).
ALTER TABLE holidays ADD COLUMN state VARCHAR(100);

-- Links a daily_attendance row to the approved tour request that produced
-- an ON_TOUR detail status, so the frontend can show destination/order
-- reference without a second lookup.
ALTER TABLE daily_attendance ADD COLUMN tour_request_id BIGINT REFERENCES tour_requests (id);
