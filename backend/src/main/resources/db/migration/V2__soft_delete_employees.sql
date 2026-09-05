ALTER TABLE employees ADD COLUMN deleted_at TIMESTAMPTZ;

-- Uniqueness now only applies to active (non-deleted) rows, so a
-- terminated employee's code/email can be reissued to a new hire.
ALTER TABLE employees DROP CONSTRAINT uq_employees_employee_code;
ALTER TABLE employees DROP CONSTRAINT uq_employees_email;

CREATE UNIQUE INDEX uq_employees_employee_code_active ON employees (employee_code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_employees_email_active ON employees (email) WHERE deleted_at IS NULL;
