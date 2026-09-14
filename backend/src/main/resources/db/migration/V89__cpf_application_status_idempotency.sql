-- Task 4 (CPF Loans & Advances - Apply for Loan idempotency): a double-click, browser retry, or network
-- retry against POST /withdrawal-applications must never create two CpfApplication rows. Every live
-- APPROVED rule (see cpf_withdrawal_rule_detail.max_active_concurrency) already caps an employee to 1
-- active application per purpose, but that check is a plain SELECT-then-INSERT in application code and
-- has a TOCTOU race under real concurrency - this partial unique index is the DB-level backstop
-- CpfApplicationService.apply() catches and turns into "return the existing APPLIED application" rather
-- than a duplicate row or an ugly 500. Scoped to status='APPLIED' only (not SANCTIONED/DISBURSED/etc) so
-- a member can freely re-apply for the same purpose after a prior application concludes one way or
-- another. If a future rule is ever configured with max_active_concurrency > 1 (no live rule is today -
-- verified against the current dev DB), this constraint would need to be revisited alongside it.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cpf_application_pending_per_purpose
    ON cpf_application (employee_code, purpose_id)
    WHERE status = 'APPLIED';
