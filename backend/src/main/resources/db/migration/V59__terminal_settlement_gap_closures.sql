-- Closes gaps found in an audit of the V58 Exit Formalities / Terminal
-- Settlement work:
--   1) TerminalSettlementService.approve() now debits the EL/HPL balances
--      it encashed and writes a leave_ledger_entries audit row - needs a
--      new ledger source distinct from EL_ENCASHMENT_DEBIT (the ordinary
--      discretionary LeaveEncashmentApplication workflow's own source),
--      since a terminal settlement never goes through that application's
--      reserve-then-finalize flow.
--   2) A DECEASED settlement now auto-populates terminal_settlement_beneficiaries
--      from the employee's registered employee_nominees (name/relationship/
--      share%) - which carry no bank details - so HR fills those in via the
--      settlement UI before approval. bank_account_no/bank_ifsc must
--      therefore be nullable at insert time; TerminalSettlementService.approve()
--      enforces they're non-blank before allowing APPROVED, so the DB
--      constraint being relaxed doesn't weaken what's actually required by
--      the time money moves.

ALTER TABLE leave_ledger_entries DROP CONSTRAINT ck_leave_ledger_entries_source;
ALTER TABLE leave_ledger_entries ADD CONSTRAINT ck_leave_ledger_entries_source CHECK (source IN
    ('AUTO_LATE_DEDUCTION', 'COMMUTED_LEAVE_HPL_DEBIT', 'BASELINE_TAKEON', 'EL_SEMI_ANNUAL_ACCRUAL',
     'EL_EOL_LAPSE_DEDUCTION', 'EL_ENCASHMENT_DEBIT', 'ATTENDANCE_PENALTY_REFUND', 'TRANSFER_JT_CONVERSION',
     'TERMINAL_ENCASHMENT'));

ALTER TABLE terminal_settlement_beneficiaries ALTER COLUMN bank_account_no DROP NOT NULL;
ALTER TABLE terminal_settlement_beneficiaries ALTER COLUMN bank_ifsc DROP NOT NULL;
