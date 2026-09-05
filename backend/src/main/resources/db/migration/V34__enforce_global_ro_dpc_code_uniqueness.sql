-- RO/DPC status-governance task, Section 1.A: ro_code/dpc_code must never be
-- reusable, even by a deactivated or soft-deleted office - historical audit
-- trails (service book, incumbency, payroll runs) reference these codes and
-- a reused code would corrupt that history. The V4/V20/V21 partial indexes
-- (WHERE deleted_at IS NULL) only enforced uniqueness among non-deleted rows,
-- which allowed a soft-deleted row's code to be reissued to a new office -
-- replace them with unconditional table-wide UNIQUE constraints.
DROP INDEX IF EXISTS uq_ro_master_ro_code_active;
DROP INDEX IF EXISTS uq_dpc_master_dpc_code_active;

-- ro_master already has an unconditional uq_ro_master_ro_code constraint
-- (added in V20). Leave it as-is: dpc_master_ro_code_fkey (dpc_master.ro_code
-- -> ro_master.ro_code) depends on the index backing it, so dropping and
-- recreating it here would fail with "cannot drop constraint ... because
-- other objects depend on it" unless the FK were dropped and rebuilt too -
-- unnecessary churn for a constraint that's already correct.

ALTER TABLE dpc_master DROP CONSTRAINT IF EXISTS uq_dpc_master_dpc_code;
ALTER TABLE dpc_master ADD CONSTRAINT uq_dpc_master_dpc_code UNIQUE (dpc_code);
