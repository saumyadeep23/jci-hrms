-- SEC-003/SEC-008: actor attribution on the CPF loan lifecycle (docs/security/MAKER_CHECKER_IMPLEMENTATION.md).
-- All four columns nullable - historical rows are not backfilled (no actor identity exists to
-- fabricate one from). Additive only; never touches V1-V93.
--
-- Deliberately plain BIGINT, no FK to employees(id) - matching the established convention this
-- codebase already uses for actor-attribution columns (e.g. jcieccs_recovery.created_by, V87).
-- The value is compared for maker != checker enforcement only; it is never joined against
-- employees, and requiring FK integrity here would be an unnecessary constraint this identical
-- existing pattern doesn't impose either.

ALTER TABLE cpf_loan_applications
    ADD COLUMN applicant_employee_id      BIGINT,
    ADD COLUMN sanctioned_by_employee_id  BIGINT,
    ADD COLUMN disbursed_by_employee_id   BIGINT,
    ADD COLUMN rejected_by_employee_id    BIGINT;
