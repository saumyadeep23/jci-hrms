package in.gov.jci.hrms.entity;

/**
 * cpf_withdrawal_rule_detail.repayment_credit_method - which head(s) a recovery re-credits, and in what
 * order (cpf_rule_head_eligibility.recredit_priority). Only {@link #ORIGINAL_DEBIT_HEAD} - the value every
 * live seed row already uses - has an actual, verified re-credit posting-path implementation; see
 * CpfWithdrawalRuleEngine's own javadoc for why the other four are stored/configurable but not yet wired.
 */
public enum CpfRepaymentCreditMethod {
    ORIGINAL_DEBIT_HEAD,
    CONFIGURED_PRIORITY,
    PROPORTIONAL,
    SPECIFIC_HEAD,
    OTHER_TRUST_RULE
}
