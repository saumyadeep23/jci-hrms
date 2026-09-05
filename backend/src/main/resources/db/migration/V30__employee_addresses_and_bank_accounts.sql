-- Feature: dynamic address (India Post API) and SCD Type-2 banking ledger -
-- PIMS_SPEC.md Steps 2 & 3. These are the normalized source of truth;
-- employees.present_*/permanent_*/bank_* stay as a denormalized "current
-- snapshot" cache (bank_* kept in sync by the trigger below - see
-- fn_sync_employee_bank_change).
CREATE TABLE IF NOT EXISTS employee_addresses (
    id              BIGSERIAL PRIMARY KEY,
    employee_id     BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    address_type    VARCHAR(20)  NOT NULL,
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    post_office     VARCHAR(150),
    police_station  VARCHAR(150),
    city            VARCHAR(100) NOT NULL,
    district        VARCHAR(100) NOT NULL,
    state           VARCHAR(100) NOT NULL,
    pin_code        VARCHAR(10)  NOT NULL,
    is_active       BOOLEAN      NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT uq_emp_address_type UNIQUE (employee_id, address_type),
    CONSTRAINT employee_addresses_address_type_check CHECK (address_type IN ('PERMANENT', 'PRESENT', 'COMMUNICATION')),
    CONSTRAINT employee_addresses_pin_code_check CHECK (pin_code ~ '^[1-9][0-9]{5}$')
);

CREATE INDEX IF NOT EXISTS idx_emp_addresses_emp_id ON employee_addresses (employee_id);

CREATE TABLE IF NOT EXISTS employee_bank_accounts (
    id                        BIGSERIAL PRIMARY KEY,
    employee_id               BIGINT       NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    bank_name                 VARCHAR(150) NOT NULL,
    bank_branch                VARCHAR(150) NOT NULL,
    bank_account_number         VARCHAR(35)  NOT NULL,
    bank_ifsc                   VARCHAR(11)  NOT NULL,
    account_type                VARCHAR(20)  NOT NULL DEFAULT 'SALARY',
    is_primary_disbursal         BOOLEAN      NOT NULL DEFAULT true,
    status                       VARCHAR(25)  NOT NULL DEFAULT 'ACTIVE',
    effective_from               DATE         NOT NULL DEFAULT CURRENT_DATE,
    effective_to                 DATE,
    change_reason                TEXT,
    cancelled_cheque_s3_key      VARCHAR(500),
    verified_by                  VARCHAR(150),
    verified_at                  TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at                   TIMESTAMPTZ,

    CONSTRAINT employee_bank_accounts_account_type_check CHECK (account_type IN ('SAVINGS', 'CURRENT', 'SALARY')),
    CONSTRAINT employee_bank_accounts_bank_ifsc_check CHECK (bank_ifsc ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    CONSTRAINT employee_bank_accounts_status_check
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'HISTORICAL', 'REJECTED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_idx_single_active_primary_bank
    ON employee_bank_accounts (employee_id) WHERE is_primary_disbursal = true AND status = 'ACTIVE' AND deleted_at IS NULL;

-- SCD Type-2: inserting (or reactivating) the new active primary account
-- archives every other ACTIVE row for the same employee as HISTORICAL and
-- closes its effective_to the day before the new one starts, then refreshes
-- employees' denormalized bank_* snapshot columns to match.
CREATE OR REPLACE FUNCTION fn_sync_employee_bank_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status = 'ACTIVE' AND NEW.is_primary_disbursal = true THEN
        UPDATE employee_bank_accounts
        SET status = 'HISTORICAL',
            is_primary_disbursal = false,
            effective_to = NEW.effective_from - INTERVAL '1 day',
            updated_at = now()
        WHERE employee_id = NEW.employee_id
          AND id <> NEW.id
          AND status = 'ACTIVE';

        UPDATE employees
        SET bank_name = NEW.bank_name,
            bank_branch = NEW.bank_branch,
            bank_account_number = NEW.bank_account_number,
            bank_ifsc = NEW.bank_ifsc,
            updated_at = now()
        WHERE id = NEW.employee_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_employee_bank_change ON employee_bank_accounts;
CREATE TRIGGER trg_employee_bank_change
    AFTER INSERT OR UPDATE OF status, is_primary_disbursal ON employee_bank_accounts
    FOR EACH ROW EXECUTE FUNCTION fn_sync_employee_bank_change();
