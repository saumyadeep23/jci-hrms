package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.service.CpfWithdrawalRuleEngine;

import java.math.BigDecimal;

/** One evaluated ceiling component (Part 11) - structured sibling of calculationTrace's free text, for
 * the Loan Simulator / Apply for Loan to render "component / source metric / value" as real fields. */
public record CeilingComponentResponse(String componentName, String sourceMetric, BigDecimal calculatedValue) {
    public static CeilingComponentResponse from(CpfWithdrawalRuleEngine.CeilingComponentValue value) {
        return new CeilingComponentResponse(value.componentName(), value.sourceMetric(), value.calculatedValue());
    }
}
