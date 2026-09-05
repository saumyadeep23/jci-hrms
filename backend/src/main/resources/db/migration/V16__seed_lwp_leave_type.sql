-- LWP (Leave Without Pay / Dies-Non) is the terminal marker
-- AttendanceLeaveDeductionService (Phase C) writes to when an employee's CL
-- and EL balances are both exhausted at the moment of an auto-debit - not a
-- leave type an employee accrues or applies for through the normal leave
-- application flow. annual_quota is 0 on purpose (nothing is credited); it
-- has no LeaveBalance row provisioned or maintained anywhere - see
-- AttendanceLeaveDeductionService for why that's a deliberate exception to
-- the "ledger writes are always lockstep with a LeaveBalance mutation" rule.
INSERT INTO leave_types (code, name, annual_quota, max_accumulation_days, is_encashable, career_limit_days, is_active) VALUES
    ('LWP', 'Leave Without Pay / Dies-Non', 0.0, NULL, false, NULL, true);
