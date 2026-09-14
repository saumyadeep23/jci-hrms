-- payroll_monthly_records.desgn_code was sized varchar(20), but PayrollBatchComputationService
-- persists it as the employee's raw Designation.title (designations.title is varchar(100) free
-- text, e.g. "DEPUTY GENERAL MANAGER (FINANCE)") since desgn_code has no dedicated short-code
-- column anywhere in the schema. Any employee whose title exceeds 20 characters made every
-- INSERT into payroll_monthly_records throw "value too long for type character varying(20)" -
-- an unhandled DataIntegrityViolationException that rolled back the whole batch computation
-- transaction, leaving payroll_batches.total_employees at 0 even though earlier employees in
-- the same run had already been computed successfully. Widened to match designations.title
-- exactly rather than picking an arbitrary larger cap.
ALTER TABLE payroll_monthly_records ALTER COLUMN desgn_code TYPE VARCHAR(100);
