-- Rejection remarks for an incoming fund transfer - no column existed for this on the live
-- employee_incoming_fund_transfers table (IncomingTransferStatus already had REJECTED, but nowhere to
-- record why).
ALTER TABLE employee_incoming_fund_transfers ADD COLUMN IF NOT EXISTS rejection_remarks TEXT;
