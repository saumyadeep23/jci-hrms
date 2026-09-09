-- Same class of drift V61 fixed for increment_cycle, found while writing that migration's own
-- regression test: regular_pay_fixations.fixation_reason's CHECK constraint on the live dev DB
-- already only accepted ('INITIAL_FIXATION','ONBOARDING','PROMOTION','ANNUAL_INCREMENT',
-- 'FINANCIAL_UPGRADATION','PAY_REVISION','DEMOTION','MIGRATION') - not V50's original
-- ('INITIAL_APPOINTMENT','PROMOTION','ANNUAL_INCREMENT','CORRECTION') - again with no migration
-- ever committed to match. Unlike increment_cycle, this one hadn't crashed anything YET: every one
-- of the 201 real rows happens to use ANNUAL_INCREMENT (valid on both sides), and only
-- EmployeeOnboardingService actually set the divergent value (INITIAL_APPOINTMENT) - meaning every
-- new REGULAR employee's first pay fixation was one onboarding away from failing its INSERT with a
-- constraint violation. This migration versions the constraint the dev DB was already enforcing;
-- FixationReason.java is widened to the same eight values in the same commit (INITIAL_APPOINTMENT
-- and CORRECTION are dropped, not kept as aliases - neither is ever exposed on a request DTO, so
-- there was no compatibility surface to preserve, and keeping either would just leave a Java value
-- the DB still rejects).
ALTER TABLE regular_pay_fixations DROP CONSTRAINT IF EXISTS ck_regular_pay_fixations_fixation_reason;

ALTER TABLE regular_pay_fixations ADD CONSTRAINT ck_regular_pay_fixations_fixation_reason
    CHECK (fixation_reason IN ('INITIAL_FIXATION', 'ONBOARDING', 'PROMOTION', 'ANNUAL_INCREMENT',
                                'FINANCIAL_UPGRADATION', 'PAY_REVISION', 'DEMOTION', 'MIGRATION'));
