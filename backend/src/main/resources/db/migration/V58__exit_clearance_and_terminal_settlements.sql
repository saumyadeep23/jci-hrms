-- Exit Formalities (multi-department clearance) + Greenfield Terminal
-- Settlement engine (Gratuity, CPF, DoPT Rule 39 EL/HPL encashment) +
-- Beneficiary Disbursement for deceased cases.
--
-- Versioned V58, not V57: V57 (this session, same day) already renamed
-- vw_jci_employee_master_360 to read regular_pay_fixations.

CREATE TABLE exit_clearance_requests (
    id                    BIGSERIAL PRIMARY KEY,
    employee_id           BIGINT NOT NULL REFERENCES employees(id),
    separation_type       VARCHAR(50) NOT NULL CHECK (separation_type IN ('SUPERANNUATION', 'RESIGNATION', 'VRS', 'DECEASED', 'TERMINATED')),
    initiated_date        DATE NOT NULL DEFAULT CURRENT_DATE,
    target_release_date   DATE NOT NULL,
    status                VARCHAR(50) NOT NULL DEFAULT 'INITIATED' CHECK (status IN ('INITIATED', 'CLEARANCE_IN_PROGRESS', 'CLEARANCES_COMPLETED', 'RELEASE_ORDER_ISSUED', 'CANCELLED')),
    release_order_ref_no  VARCHAR(100),
    release_order_date    DATE,
    remarks               TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE exit_clearance_items (
    id                     BIGSERIAL PRIMARY KEY,
    clearance_request_id  BIGINT NOT NULL REFERENCES exit_clearance_requests(id) ON DELETE CASCADE,
    department_code       VARCHAR(50) NOT NULL CHECK (department_code IN ('ESTABLISHMENT', 'VIGILANCE', 'ESTATE', 'IT', 'FINANCE', 'STORES', 'CPF_TRUST')),
    status                 VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CLEARED', 'REJECTED_WITH_DUES')),
    dues_recovery_amount  NUMERIC(12,2) DEFAULT 0.00,
    remarks                TEXT,
    cleared_by_user_id    BIGINT,
    cleared_at             TIMESTAMPTZ
);

CREATE TABLE terminal_settlements (
    id                            BIGSERIAL PRIMARY KEY,
    employee_id                   BIGINT NOT NULL REFERENCES employees(id),
    clearance_request_id          BIGINT REFERENCES exit_clearance_requests(id),
    separation_type               VARCHAR(50) NOT NULL CHECK (separation_type IN ('SUPERANNUATION', 'RESIGNATION', 'VRS', 'DECEASED', 'TERMINATED')),
    separation_date               DATE NOT NULL,
    last_basic_pay                NUMERIC(12,2) NOT NULL,
    da_rate_percentage            NUMERIC(5,2) NOT NULL,
    da_amount                     NUMERIC(12,2) NOT NULL,
    qualifying_service_years      INTEGER NOT NULL,
    qualifying_service_months     INTEGER NOT NULL,
    el_balance_at_retirement      NUMERIC(5,2) NOT NULL,
    hpl_balance_at_retirement     NUMERIC(5,2) NOT NULL,
    el_days_encashed              NUMERIC(5,2) NOT NULL,
    hpl_days_encashed             NUMERIC(5,2) NOT NULL,
    leave_encashment_el_amount    NUMERIC(12,2) NOT NULL,
    leave_encashment_hpl_amount   NUMERIC(12,2) NOT NULL,
    total_leave_encashment        NUMERIC(12,2) NOT NULL,
    gratuity_amount                NUMERIC(12,2) NOT NULL,
    is_death_gratuity             BOOLEAN NOT NULL DEFAULT FALSE,
    cpf_employee_balance          NUMERIC(12,2) DEFAULT 0.00,
    cpf_employer_balance          NUMERIC(12,2) DEFAULT 0.00,
    cpf_vpf_balance               NUMERIC(12,2) DEFAULT 0.00,
    cpf_accrued_interest          NUMERIC(12,2) DEFAULT 0.00,
    total_cpf_payable             NUMERIC(14,2) DEFAULT 0.00,
    gross_terminal_dues           NUMERIC(14,2) NOT NULL,
    total_recoveries_deductions   NUMERIC(12,2) DEFAULT 0.00,
    net_terminal_payable          NUMERIC(14,2) NOT NULL,
    status                         VARCHAR(50) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'AUDITED', 'APPROVED', 'DISBURSED')),
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE terminal_settlement_beneficiaries (
    id                   BIGSERIAL PRIMARY KEY,
    settlement_id       BIGINT NOT NULL REFERENCES terminal_settlements(id) ON DELETE CASCADE,
    beneficiary_type     VARCHAR(50) NOT NULL CHECK (beneficiary_type IN ('SELF', 'NOMINEE', 'LEGAL_HEIR')),
    beneficiary_name     VARCHAR(150) NOT NULL,
    relationship          VARCHAR(50) NOT NULL,
    share_percentage    NUMERIC(5,2) NOT NULL CHECK (share_percentage > 0 AND share_percentage <= 100),
    allocated_amount     NUMERIC(14,2) NOT NULL,
    bank_account_no      VARCHAR(50) NOT NULL,
    bank_ifsc            VARCHAR(20) NOT NULL,
    bank_name             VARCHAR(100),
    pan_number            VARCHAR(20),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_exit_clearance_emp ON exit_clearance_requests(employee_id);
CREATE INDEX idx_exit_clearance_status ON exit_clearance_requests(status);
CREATE INDEX idx_exit_clearance_items_req ON exit_clearance_items(clearance_request_id);
CREATE INDEX idx_terminal_settlements_emp ON terminal_settlements(employee_id);
CREATE INDEX idx_terminal_beneficiaries_settlement ON terminal_settlement_beneficiaries(settlement_id);
