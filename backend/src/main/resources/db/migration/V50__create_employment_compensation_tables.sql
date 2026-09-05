-- Additive, historized compensation ledgers, one table per employment tier - unlike
-- employee_employment_categories (V29/V49, one CURRENT row per employee, overwritten on change),
-- these three keep every past fixation/engagement/deployment (is_current flags the live one), which
-- promotion pay fixation (regular_pay_fixations) and contract/deployment renewal (the other two)
-- need a real history to work against. employee_employment_categories is left as-is and keeps
-- backing payroll/LPC/etc. unchanged - EmployeeOnboardingService now writes to both on onboarding,
-- MovementOrderService keeps employee_employment_categories in sync on promotion.
CREATE TABLE regular_pay_fixations (
    id               BIGSERIAL PRIMARY KEY,
    employee_id      BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    scale_code       VARCHAR(10)  NOT NULL REFERENCES grade_scale_master (scale_code),
    basic_pay        NUMERIC(12,2) NOT NULL,
    effective_from   DATE         NOT NULL,
    effective_to     DATE,
    increment_cycle  VARCHAR(10)  NOT NULL DEFAULT 'JULY',
    fixation_reason  VARCHAR(100) NOT NULL DEFAULT 'INITIAL_APPOINTMENT',
    order_ref_no     VARCHAR(100),
    is_current       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT ck_regular_pay_fixations_increment_cycle CHECK (increment_cycle IN ('JULY', 'JANUARY')),
    CONSTRAINT ck_regular_pay_fixations_fixation_reason CHECK (fixation_reason IN ('INITIAL_APPOINTMENT', 'PROMOTION', 'ANNUAL_INCREMENT', 'CORRECTION'))
);

CREATE UNIQUE INDEX idx_uq_current_regular_fixation ON regular_pay_fixations (employee_id) WHERE is_current = TRUE;
CREATE INDEX idx_regular_pay_fixations_employee_id ON regular_pay_fixations (employee_id);

CREATE TABLE contractual_engagements (
    id                   BIGSERIAL PRIMARY KEY,
    employee_id          BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    scale_code           VARCHAR(10)  REFERENCES grade_scale_master (scale_code),
    monthly_lumpsum      NUMERIC(12,2) NOT NULL,
    contract_start_date  DATE         NOT NULL,
    contract_end_date    DATE         NOT NULL,
    approval_ref_no      VARCHAR(100) NOT NULL,
    engagement_terms     TEXT,
    is_current           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT ck_contractual_engagements_dates CHECK (contract_end_date >= contract_start_date)
);

CREATE UNIQUE INDEX idx_uq_current_contractual_engagement ON contractual_engagements (employee_id) WHERE is_current = TRUE;
CREATE INDEX idx_contractual_engagements_employee_id ON contractual_engagements (employee_id);

CREATE TABLE outsourced_deployments (
    id                     BIGSERIAL PRIMARY KEY,
    employee_id            BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    vendor_id              BIGINT REFERENCES vendor_master (id),
    scale_code             VARCHAR(10)  REFERENCES grade_scale_master (scale_code),
    monthly_ctc            NUMERIC(12,2) NOT NULL,
    agency_billing_rate    NUMERIC(12,2),
    deployment_start_date  DATE         NOT NULL,
    deployment_end_date    DATE         NOT NULL,
    work_order_ref         VARCHAR(100) NOT NULL,
    is_current             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT ck_outsourced_deployments_dates CHECK (deployment_end_date >= deployment_start_date)
);

CREATE UNIQUE INDEX idx_uq_current_outsourced_deployment ON outsourced_deployments (employee_id) WHERE is_current = TRUE;
CREATE INDEX idx_outsourced_deployments_employee_id ON outsourced_deployments (employee_id);
