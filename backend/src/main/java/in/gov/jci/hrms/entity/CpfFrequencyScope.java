package in.gov.jci.hrms.entity;

/** cpf_withdrawal_rule_detail.frequency_scope - the counting window CpfWithdrawalRuleEngine.evaluateFrequency() applies max_occurrences over. SERVICE and NONE are already used by the live seed data; the other three are supported for future rules. */
public enum CpfFrequencyScope {
    SERVICE,
    FINANCIAL_YEAR,
    CALENDAR_YEAR,
    ROLLING_PERIOD,
    NONE
}
