CREATE TABLE employees (
    id               BIGSERIAL PRIMARY KEY,
    employee_code    VARCHAR(50)  NOT NULL,
    first_name       VARCHAR(100) NOT NULL,
    last_name        VARCHAR(100) NOT NULL,
    email            VARCHAR(255) NOT NULL,
    phone            VARCHAR(20),
    date_of_birth    DATE,
    date_of_joining  DATE         NOT NULL,
    department       VARCHAR(100),
    designation      VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_employees_employee_code UNIQUE (employee_code),
    CONSTRAINT uq_employees_email UNIQUE (email),
    CONSTRAINT ck_employees_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'TERMINATED'))
);
