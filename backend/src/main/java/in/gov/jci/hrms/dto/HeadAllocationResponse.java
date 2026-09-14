package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** One head's configured debit priority (CpfRuleHeadEligibility.debitPriority) plus, when a preview
 * amount is known, the projected debit against it (CpfWithdrawalRuleEngine.allocateDebitAcrossHeads) -
 * never a hardcoded "VPF first" - the priority order/values come straight from the rule configuration. */
public record HeadAllocationResponse(String headCode, String headName, int debitPriority, BigDecimal previewDebitAmount) {
}
