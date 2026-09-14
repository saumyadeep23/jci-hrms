-- Payroll Deduction Cap (Part 18 of the CPF Trust Loan & Advances rule-engine spec) - a statutory ceiling
-- independent of any specific withdrawal rule (cpf_withdrawal_rule_detail.payroll_cap_type is just a label,
-- e.g. "NORMAL"/"COOPERATIVE", that resolves against whichever row here is currently active). Unlike
-- cpf_withdrawal_rule_version's full DRAFT/PENDING_APPROVAL/APPROVED workflow, this uses the simpler
-- revise-and-close versioning payroll_statutory_parameters already established in this codebase - a single
-- statutory percentage doesn't need the same Trust Secretariat sign-off a withdrawal ceiling policy does.
--
-- No confirmed JCI-specific payroll deduction cap exists anywhere in this codebase or its SRS - the seeded
-- row below is REQUIRES_CONFIRMATION reference data (Part 33), not asserted as JCI's actual approved policy.
CREATE TABLE IF NOT EXISTS cpf_payroll_deduction_cap (
    id                    BIGSERIAL     PRIMARY KEY,
    normal_percent        NUMERIC(5,2)  NOT NULL,
    cooperative_percent   NUMERIC(5,2),
    applicability         VARCHAR(200),
    legal_reference       VARCHAR(200),
    effective_from        DATE          NOT NULL,
    effective_to          DATE,
    remarks               TEXT,
    created_at            TIMESTAMPTZ   DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_cpf_payroll_deduction_cap_current ON cpf_payroll_deduction_cap (effective_from) WHERE effective_to IS NULL;

INSERT INTO cpf_payroll_deduction_cap (normal_percent, cooperative_percent, applicability, legal_reference, effective_from, remarks)
SELECT 50.00, 75.00, 'All CPF Trust refundable loan/advance recoveries', 'REQUIRES_CONFIRMATION - no confirmed JCI/statutory source found in this codebase',
       DATE '2020-04-01', 'INITIAL JCI CPF TRUST CONFIGURATION - placeholder pending Trust/legal confirmation.'
WHERE NOT EXISTS (SELECT 1 FROM cpf_payroll_deduction_cap);
