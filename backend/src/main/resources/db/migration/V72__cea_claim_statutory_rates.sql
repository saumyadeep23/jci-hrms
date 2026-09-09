-- CEA (Children Education Allowance) monthly reimbursement rates, consumed by CeaClaimService when
-- computing a claim's admissible amount. employee_cea_claims itself already exists in the shared dev
-- database (created directly by other tooling before this migration existed - same situation as
-- V66/67/68/69/71's own comments), so this migration only adds the rate parameters, not the table.
INSERT INTO payroll_statutory_parameters (param_key, param_name, param_value, val_type, effective_from, remarks) VALUES
    ('CEA_STANDARD_RATE', 'Children Education Allowance - Standard Monthly Rate', 2812.50, 'AMOUNT', '2020-04-01', 'Per child per month, up to 2 children, age <= 20 - Head 22 (CEA)'),
    ('CEA_DIVYANG_RATE', 'Children Education Allowance - Divyang Monthly Rate', 5625.00, 'AMOUNT', '2020-04-01', 'Double the standard rate for a Divyang/PwD child, age <= 22 - Head 22 (CEA)'),
    ('CEA_HOSTEL_SUBSIDY_RATE', 'Hostel Subsidy - Monthly Rate', 8437.50, 'AMOUNT', '2020-04-01', 'claim_type = HOSTEL_SUBSIDY - Head 22 (CEA)')
ON CONFLICT ON CONSTRAINT uq_payroll_param_key_date DO NOTHING;
