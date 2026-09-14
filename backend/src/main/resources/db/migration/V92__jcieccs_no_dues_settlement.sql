-- JCIECCS Lifecycle Engine Phase 3: integrates JCIECCS into the EXISTING HR exit-clearance workflow
-- (exit_clearance_requests / exit_clearance_items, V58) as an 8th department, rather than building a
-- second separation/settlement status engine. jcieccs_settlement is only the detailed calculation
-- snapshot (share/fund/security/thrift + TE/EM outstanding breakdown) that feeds the one aggregate
-- exit_clearance_items.dues_recovery_amount figure for the JCIECCS department row.

ALTER TABLE exit_clearance_items DROP CONSTRAINT exit_clearance_items_department_code_check;
ALTER TABLE exit_clearance_items ADD CONSTRAINT exit_clearance_items_department_code_check
    CHECK (department_code IN ('ESTABLISHMENT', 'VIGILANCE', 'ESTATE', 'IT', 'FINANCE', 'STORES', 'CPF_TRUST', 'JCIECCS'));

CREATE TABLE IF NOT EXISTS jcieccs_settlement (
    id                              BIGSERIAL PRIMARY KEY,
    member_id                       BIGINT      NOT NULL REFERENCES jcieccs_member (id),
    employee_id                     BIGINT      NOT NULL,
    exit_clearance_request_id       BIGINT      NULL REFERENCES exit_clearance_requests (id),
    exit_clearance_item_id          BIGINT      NULL REFERENCES exit_clearance_items (id),
    separation_type                 VARCHAR(50) NULL,
    separation_date                 DATE        NULL,

    share_balance                   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    fund_balance                    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    security_balance                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    thrift_balance                  NUMERIC(14, 2) NOT NULL DEFAULT 0,

    term_principal_outstanding      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    term_interest_outstanding       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    emergency_principal_outstanding NUMERIC(14, 2) NOT NULL DEFAULT 0,
    emergency_interest_outstanding  NUMERIC(14, 2) NOT NULL DEFAULT 0,
    other_dues                      NUMERIC(14, 2) NOT NULL DEFAULT 0,

    -- Never computed - no approved JCIECCS set-off rule exists in this codebase (spec Task4-Phase3
    -- section 31: REQUIRES_BUSINESS_CONFIRMATION). Always 0 until an authoritative rule is implemented;
    -- net_liability is therefore always the gross outstanding loan liability, never netted against
    -- share/fund/security/thrift.
    setoff_amount                   NUMERIC(14, 2) NOT NULL DEFAULT 0,
    net_liability                   NUMERIC(14, 2) NOT NULL DEFAULT 0,

    stale                           BOOLEAN     NOT NULL DEFAULT FALSE,

    calculated_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_by                   BIGINT      NULL,
    remarks                         VARCHAR(500) NULL,

    version                         BIGINT      NOT NULL DEFAULT 0,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_jcieccs_settlement_member UNIQUE (member_id)
);

CREATE INDEX IF NOT EXISTS idx_jcieccs_settlement_employee ON jcieccs_settlement (employee_id);
