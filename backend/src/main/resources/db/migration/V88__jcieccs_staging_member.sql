-- Historical Migration Service (spec section 4.6): stages co-operative membership codes + audited FY
-- 2023-24 opening balances before promoting them into real jcieccs_member rows, mirroring the existing
-- staging -> validate -> promote workflow (staging_legacy_loans / LegacyMigrationService) - status reuses
-- the same generic staging_row_status vocabulary (PENDING/PROMOTED/REJECTED) that table already uses.
CREATE TABLE IF NOT EXISTS jcieccs_staging_member (
    id                     BIGSERIAL PRIMARY KEY,
    employee_code          VARCHAR(50)  NOT NULL,
    membership_code        VARCHAR(50)  NOT NULL,
    membership_date        DATE         NOT NULL,
    share_balance          NUMERIC(14, 2) NOT NULL DEFAULT 0,
    fund_balance           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    security_balance       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    thrift_monthly_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status                 VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    rejection_reason       VARCHAR(500) NULL,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_jcieccs_staging_member_status CHECK (status IN ('PENDING', 'PROMOTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_staging_member_status ON jcieccs_staging_member (status);
