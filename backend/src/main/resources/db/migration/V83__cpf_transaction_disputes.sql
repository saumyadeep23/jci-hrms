-- CPF Transaction Dispute module (Passbook V2) - an external workflow that OBSERVES/REFERENCES an
-- immutable cpf_trust_member_ledger_entries row by id; it never duplicates, edits, or reverses ledger data.
-- No accounting column (amount, running balance, contribution, EPS) exists anywhere on this table - see
-- CpfTransactionDispute's own javadoc for the non-negotiable accounting-safety rule this schema enforces
-- by construction (there is simply no column here a reviewer action could use to alter the ledger).
CREATE TABLE IF NOT EXISTS cpf_transaction_disputes (
    id                             BIGSERIAL     PRIMARY KEY,
    dispute_number                 VARCHAR(30)   NOT NULL UNIQUE,
    employee_id                    BIGINT        NOT NULL REFERENCES employees (id),
    cpf_ledger_transaction_id      BIGINT        NOT NULL REFERENCES cpf_trust_member_ledger_entries (id),
    dispute_category               VARCHAR(30)   NOT NULL,
    status                         VARCHAR(30)   NOT NULL DEFAULT 'OPEN',

    employee_remarks               TEXT          NOT NULL,
    attachment_s3_key              VARCHAR(500),
    attachment_original_filename   VARCHAR(255),

    raised_by                      BIGINT        NOT NULL REFERENCES employees (id),
    raised_at                      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    assigned_to                    BIGINT        REFERENCES employees (id),
    assigned_at                    TIMESTAMPTZ,

    reviewer_remarks               TEXT,
    clarification_request          TEXT,
    employee_response              TEXT,
    resolution_remarks             TEXT,

    resolved_by                    BIGINT        REFERENCES employees (id),
    resolved_at                    TIMESTAMPTZ,
    rejected_by                    BIGINT        REFERENCES employees (id),
    rejected_at                    TIMESTAMPTZ,
    withdrawn_at                   TIMESTAMPTZ,

    created_at                     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                     TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                        BIGINT        NOT NULL DEFAULT 0
);

DO $$ BEGIN
    ALTER TABLE cpf_transaction_disputes ADD CONSTRAINT ck_cpf_dispute_category CHECK (dispute_category IN
        ('EMPLOYEE_CONTRIBUTION', 'EMPLOYER_CONTRIBUTION', 'EPS_CONTRIBUTION', 'VPF_CONTRIBUTION', 'INTEREST',
         'LOAN_SANCTION', 'LOAN_REPAYMENT', 'WITHDRAWAL', 'TRANSACTION_MISSING', 'BALANCE', 'TRANSACTION_DATE', 'OTHER'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
DO $$ BEGIN
    ALTER TABLE cpf_transaction_disputes ADD CONSTRAINT ck_cpf_dispute_status CHECK (status IN
        ('OPEN', 'UNDER_REVIEW', 'CLARIFICATION_REQUIRED', 'RESOLVED', 'REJECTED', 'WITHDRAWN'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE INDEX IF NOT EXISTS idx_cpf_dispute_employee ON cpf_transaction_disputes (employee_id);
CREATE INDEX IF NOT EXISTS idx_cpf_dispute_ledger_txn ON cpf_transaction_disputes (cpf_ledger_transaction_id);
CREATE INDEX IF NOT EXISTS idx_cpf_dispute_status ON cpf_transaction_disputes (status);
CREATE INDEX IF NOT EXISTS idx_cpf_dispute_raised_at ON cpf_transaction_disputes (raised_at);
CREATE INDEX IF NOT EXISTS idx_cpf_dispute_employee_status ON cpf_transaction_disputes (employee_id, status);
CREATE INDEX IF NOT EXISTS idx_cpf_dispute_employee_category_status ON cpf_transaction_disputes (employee_id, dispute_category, status);

-- Duplicate-active-dispute protection (Part 10): at most one dispute per (transaction, category) may be
-- ACTIVE (not RESOLVED/REJECTED/WITHDRAWN) at a time - a properly closed-out transaction can always be
-- disputed again later (e.g. a new issue, or a reopened conversation), so this is a partial index, never a
-- blanket unique constraint on (transaction, category) alone.
CREATE UNIQUE INDEX IF NOT EXISTS ux_cpf_dispute_active_per_txn_category
    ON cpf_transaction_disputes (cpf_ledger_transaction_id, dispute_category)
    WHERE status NOT IN ('RESOLVED', 'REJECTED', 'WITHDRAWN');
