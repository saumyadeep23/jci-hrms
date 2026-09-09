-- In-service EL encashment gross-amount computation (CPSE formula: (Basic + DA) / 30 * days) and
-- retroactive-DA-arrear tracking. da_rate_applied/da_effective_date/gross_amount are snapshotted at
-- apply() time (the rate actually used to quote the claim); if the DA rate is later revised upward
-- with retroactive effect before Finance (Gate 2) sanctions the claim, financeApprove() computes
-- arrear_amount against the newer rate and flips is_arrear_settled to false - see
-- LeaveEncashmentService for both computations.
ALTER TABLE leave_encashment_application
    ADD COLUMN da_rate_applied   NUMERIC(6, 2),
    ADD COLUMN gross_amount      NUMERIC(12, 2),
    ADD COLUMN da_effective_date DATE,
    ADD COLUMN is_arrear_settled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN arrear_amount     NUMERIC(12, 2) NOT NULL DEFAULT 0.00;

-- One-time backfill for applications submitted before this column existed (this dev DB's own test
-- data included) - snapshots today's basic pay / active DA rate rather than leaving them null,
-- since apply()'s own snapshot moment has already passed for these rows.
UPDATE leave_encashment_application lea
SET da_rate_applied   = drh.da_percentage,
    da_effective_date = drh.effective_from,
    gross_amount      = ROUND((rpf.basic_pay + (rpf.basic_pay * drh.da_percentage / 100)) / 30 * lea.el_days_claimed, 2)
FROM regular_pay_fixations rpf
JOIN grade_scale_master gsm ON gsm.scale_code = rpf.scale_code
JOIN LATERAL (
    SELECT da_percentage, effective_from
    FROM da_rate_history
    WHERE scale_type = gsm.scale_type AND is_active = TRUE AND effective_from <= CURRENT_DATE
    ORDER BY effective_from DESC
    LIMIT 1
) drh ON TRUE
WHERE rpf.employee_id = lea.employee_id
  AND rpf.is_current = TRUE
  AND lea.gross_amount IS NULL;
