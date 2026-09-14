package in.gov.jci.hrms.entity;

/**
 * cpf_rule_ceiling_component.source_metric - what quantity a ceiling component's operator/factor_value is
 * applied to. ELIGIBLE_BALANCE/BASIC_PLUS_DA/PROPERTY_COST are already used by the live seed data and mean
 * exactly what their names say - ELIGIBLE_BALANCE in particular is always the sum of whichever heads
 * {@link CpfRuleHeadEligibility} marks eligible for that same rule detail (there is no per-component head
 * subset in this schema, unlike an earlier draft of this module). OUTSTANDING_LOAN/
 * PAYROLL_DEDUCTION_CAPACITY/FIXED_AMOUNT are supported for future rules (Part 13 of the spec) but unused
 * by any live rule today.
 */
public enum CpfCeilingSourceMetric {
    ELIGIBLE_BALANCE,
    BASIC_PLUS_DA,
    PROPERTY_COST,
    OUTSTANDING_LOAN,
    PAYROLL_DEDUCTION_CAPACITY,
    FIXED_AMOUNT
}
