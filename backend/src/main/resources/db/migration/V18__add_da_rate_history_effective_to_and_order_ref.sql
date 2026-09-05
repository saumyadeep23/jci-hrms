-- effective_to is server-computed (see DaRateHistoryService.create()), never
-- client-supplied - inserting a new rate automatically closes out whichever
-- existing row's range it lands inside, so ranges never overlap by
-- construction. order_number/order_date/remarks are reference/audit fields
-- tied to the government order that decreed the rate.
ALTER TABLE da_rate_history ADD COLUMN effective_to DATE;
ALTER TABLE da_rate_history ADD COLUMN order_number VARCHAR(50);
ALTER TABLE da_rate_history ADD COLUMN order_date DATE;
ALTER TABLE da_rate_history ADD COLUMN remarks VARCHAR(255);

CREATE UNIQUE INDEX uq_da_rate_history_scale_type_effective_from ON da_rate_history (scale_type, effective_from);
