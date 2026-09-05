-- ALMS operational gap #3: device registration/approval workflow for mobile
-- and biometric-terminal attendance punching. Distinct from mobile_punches'
-- own free-text device_id column (MobilePunchService) - that column is an
-- unvalidated per-punch label with no lifecycle; this table is the actual
-- registry an admin approves/revokes devices against, independent of
-- whether MobilePunchController ever cross-checks a punch's device_id
-- against it (it does not, in this change - see RegisteredDeviceService
-- javadoc).
CREATE TABLE registered_devices (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id),
    device_name VARCHAR(150) NOT NULL,
    device_type VARCHAR(30) NOT NULL,
    device_identifier VARCHAR(150) NOT NULL,
    platform VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    approved_by BIGINT REFERENCES employees(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One employee can't register the exact same device identifier twice (re-registering the same phone/terminal should update, not duplicate).
CREATE UNIQUE INDEX uq_registered_devices_employee_identifier ON registered_devices (employee_id, device_identifier);
CREATE INDEX idx_registered_devices_employee_id ON registered_devices (employee_id);
CREATE INDEX idx_registered_devices_status ON registered_devices (status);
