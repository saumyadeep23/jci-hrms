-- Adds REJECTED to cpf_loan_applications.status - V74's own CHECK constraint (added there since the live
-- table had none) only allowed APPLIED/SANCTIONED/DISBURSED/CLOSED, but CpfLoanApplicationService now
-- needs to reject an APPLIED loan. Safe to redefine (not "already live" data this migration must
-- preserve as-is) since V74's constraint is this codebase's own addition, not part of the pre-existing
-- live shape.
ALTER TABLE cpf_loan_applications DROP CONSTRAINT IF EXISTS ck_cpf_loan_status;
ALTER TABLE cpf_loan_applications ADD CONSTRAINT ck_cpf_loan_status CHECK (status IN ('APPLIED', 'SANCTIONED', 'DISBURSED', 'CLOSED', 'REJECTED'));

-- Rejection remarks and the applicant's own free-text justification - neither column existed on the
-- live table (purpose is only a short category code, not a justification).
ALTER TABLE cpf_loan_applications ADD COLUMN IF NOT EXISTS rejection_remarks TEXT;
ALTER TABLE cpf_loan_applications ADD COLUMN IF NOT EXISTS application_reason TEXT;
