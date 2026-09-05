CREATE TABLE medical_claims (
    id                   BIGSERIAL PRIMARY KEY,
    claim_number         VARCHAR(50)   NOT NULL,
    employee_id          BIGINT        NOT NULL REFERENCES employees (id),
    dependent_id         BIGINT REFERENCES employee_dependents (id),
    total_claimed_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_allowed_amount NUMERIC(12,2),
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    submission_date      DATE,
    verified_by          VARCHAR(150),
    approved_by_finance  VARCHAR(150),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at           TIMESTAMPTZ,

    CONSTRAINT ck_medical_claims_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'VERIFIED_BY_HR', 'APPROVED_BY_FINANCE', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_medical_claims_claim_number_active ON medical_claims (claim_number) WHERE deleted_at IS NULL;
CREATE INDEX ix_medical_claims_employee_id ON medical_claims (employee_id);

CREATE TABLE medical_claim_items (
    id               BIGSERIAL PRIMARY KEY,
    medical_claim_id BIGINT        NOT NULL REFERENCES medical_claims (id),
    expense_type     VARCHAR(20)   NOT NULL,
    claimed_amount   NUMERIC(12,2) NOT NULL,
    allowed_amount   NUMERIC(12,2),
    bill_number      VARCHAR(100)  NOT NULL,
    bill_date        DATE          NOT NULL,
    remarks          TEXT,

    CONSTRAINT ck_medical_claim_items_expense_type
        CHECK (expense_type IN ('CONSULTATION', 'MEDICINE', 'PATHOLOGY', 'HOSPITALIZATION', 'DENTAL', 'SPECTACLES', 'SURGERY'))
);

CREATE INDEX ix_medical_claim_items_medical_claim_id ON medical_claim_items (medical_claim_id);

CREATE TABLE tour_requests (
    id                  BIGSERIAL PRIMARY KEY,
    request_number      VARCHAR(50)   NOT NULL,
    employee_id         BIGINT        NOT NULL REFERENCES employees (id),
    purpose             TEXT          NOT NULL,
    origin              VARCHAR(150)  NOT NULL,
    destination         VARCHAR(150)  NOT NULL,
    start_date          DATE          NOT NULL,
    end_date            DATE          NOT NULL,
    is_post_facto       BOOLEAN       NOT NULL DEFAULT false,
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    direct_flight_cost  NUMERIC(12,2),
    direct_hotel_cost   NUMERIC(12,2),
    direct_vehicle_cost NUMERIC(12,2),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT ck_tour_requests_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_tour_requests_dates CHECK (end_date >= start_date)
);

CREATE UNIQUE INDEX uq_tour_requests_request_number_active ON tour_requests (request_number) WHERE deleted_at IS NULL;
CREATE INDEX ix_tour_requests_employee_id ON tour_requests (employee_id);

CREATE TABLE tada_claims (
    id                     BIGSERIAL PRIMARY KEY,
    tour_request_id        BIGINT        NOT NULL REFERENCES tour_requests (id),
    claim_number            VARCHAR(50)   NOT NULL,
    employee_id             BIGINT        NOT NULL REFERENCES employees (id),
    out_of_pocket_claimed   NUMERIC(12,2) NOT NULL,
    out_of_pocket_allowed   NUMERIC(12,2),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    verified_by             VARCHAR(150),
    approved_by_finance     VARCHAR(150),
    submission_date         DATE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ,

    CONSTRAINT ck_tada_claims_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'VERIFIED_BY_HR', 'APPROVED_BY_FINANCE', 'REJECTED'))
);

CREATE UNIQUE INDEX uq_tada_claims_claim_number_active ON tada_claims (claim_number) WHERE deleted_at IS NULL;
CREATE INDEX ix_tada_claims_tour_request_id ON tada_claims (tour_request_id);
CREATE INDEX ix_tada_claims_employee_id ON tada_claims (employee_id);

CREATE TABLE tada_rate_master (
    id                       BIGSERIAL PRIMARY KEY,
    designation_id           BIGINT        NOT NULL REFERENCES designations (id),
    city_class               VARCHAR(1)    NOT NULL,
    room_rent_ceiling        NUMERIC(10,2) NOT NULL,
    daily_allowance_ceiling  NUMERIC(10,2) NOT NULL,
    is_active                BOOLEAN       NOT NULL DEFAULT true,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at               TIMESTAMPTZ,

    CONSTRAINT ck_tada_rate_master_city_class CHECK (city_class IN ('X', 'Y', 'Z'))
);

CREATE UNIQUE INDEX uq_tada_rate_master_designation_city_active
    ON tada_rate_master (designation_id, city_class) WHERE deleted_at IS NULL;
