package in.gov.jci.hrms.entity;

/** cpf_rule_ceiling_component.operator - how factor_value combines with source_metric's resolved amount. MULTIPLY/PERCENTAGE are already used by the live seed data (e.g. "36 x Basic+DA", "50% of Eligible Balance"); FIXED is supported for a flat ceiling amount, unused by any live rule today. */
public enum CpfCeilingOperator {
    /** metric * factor_value (e.g. "36 months" of Basic+DA). */
    MULTIPLY,
    /** metric * factor_value / 100 (e.g. "50% of eligible balance"). */
    PERCENTAGE,
    /** factor_value itself, source_metric ignored (a flat ceiling amount). */
    FIXED
}
