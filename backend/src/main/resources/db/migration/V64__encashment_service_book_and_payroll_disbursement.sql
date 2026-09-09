-- Structured e-Service Book metadata for EL encashment entries (days_encashed/da_rate/gross_amount -
-- basic_pay already exists on this table since V12/V29; order_number/order_date, also already there,
-- double as the sanction ref/date rather than adding duplicate columns for the same concept).
ALTER TABLE employee_service_book
    ADD COLUMN days_encashed NUMERIC(5, 2),
    ADD COLUMN da_rate       NUMERIC(6, 2),
    ADD COLUMN gross_amount  NUMERIC(12, 2);

-- Payroll disbursement linkage for the base encashment amount and, separately, for its arrear (the
-- arrear can land in a LATER payroll cycle than the base amount, since it's only known once Finance
-- sanctions the claim - see LeaveEncashmentService.checkForRetroactiveArrear()). is_payroll_processed
-- flips true only once the linked run is finalized (PayrollRunService.finalizeRun()), not merely
-- computed - see that service for the queue/finalize split.
ALTER TABLE leave_encashment_application
    ADD COLUMN is_payroll_processed  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN payroll_run_id        BIGINT REFERENCES payroll_runs (id),
    ADD COLUMN arrear_da_rate_diff   NUMERIC(6, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN arrear_payroll_run_id BIGINT REFERENCES payroll_runs (id);

CREATE INDEX ix_leave_encashment_application_payroll_run_id ON leave_encashment_application (payroll_run_id);

-- New salary head so the payroll run's addItem() lookup (which throws if a code is missing) can post
-- an in-service EL encashment payout as its own line item, distinct from the arrear top-up.
INSERT INTO salary_head_master (code, name, head_type, is_variable, is_taxable) VALUES
    ('EL_ENCASHMENT', 'Earned Leave Encashment', 'EARNING', true, true),
    ('EL_ENCASHMENT_ARREAR', 'Earned Leave Encashment - DA Arrear', 'EARNING', true, true);
