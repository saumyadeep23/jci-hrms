-- HOUSING_LOAN_REPAYMENT's own ceiling component named "OUTSTANDING_LOAN_CAP" was seeded reading
-- source_metric = PROPERTY_COST instead of OUTSTANDING_LOAN - the schema already has a distinct
-- OUTSTANDING_LOAN metric (CpfCeilingSourceMetric), built specifically for this purpose but never wired
-- into the seed data. This conflated "what the property costs" with "what's still owed on the housing
-- loan being repaid" - two different figures. Confirmed with the business owner before applying:
-- cpf_application has 0 rows against this rule version, so no historical calculation is rewritten by
-- this correction - it only changes how a FUTURE eligibility check for this purpose resolves.
UPDATE cpf_rule_ceiling_component
SET source_metric = 'OUTSTANDING_LOAN'
WHERE id = 'c7caffdb-0060-40eb-a1c4-8e5f25981288'
  AND component_name = 'OUTSTANDING_LOAN_CAP'
  AND source_metric = 'PROPERTY_COST';
