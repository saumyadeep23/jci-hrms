-- Cadre (EmploymentCategory) eligibility per leave type - ALMS operational
-- gap: previously no leave type had ANY cadre restriction (every employment
-- category could apply for every leave type equally, since nothing modeled
-- eligibility at all). This table seeds that exact status quo - every
-- existing leave type is marked eligible for all 4 categories - so adding
-- it changes no current behavior until HR deliberately narrows eligibility
-- via the new Leave Type Master UI.
--
-- employment_category stores the real 4-value in.gov.jci.hrms.entity.
-- EmploymentCategory enum (REGULAR/CASUAL/CONTRACTUAL/OUTSOURCED, see
-- V-series migrations for employee_employment_categories) - not the
-- REGULAR_EXECUTIVE/REGULAR_NON_EXECUTIVE split named in the originating
-- task spec, which has no counterpart anywhere in this schema.
CREATE TABLE leave_type_cadre_eligibility (
    leave_type_id BIGINT NOT NULL REFERENCES leave_types(id),
    employment_category VARCHAR(20) NOT NULL,
    PRIMARY KEY (leave_type_id, employment_category)
);

INSERT INTO leave_type_cadre_eligibility (leave_type_id, employment_category)
SELECT lt.id, cat.employment_category
FROM leave_types lt
CROSS JOIN (VALUES ('REGULAR'), ('CASUAL'), ('CONTRACTUAL'), ('OUTSOURCED')) AS cat(employment_category);
