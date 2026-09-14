package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfFrequencyScope;
import in.gov.jci.hrms.entity.CpfRepaymentCreditMethod;
import in.gov.jci.hrms.entity.CpfRuleStatus;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CpfWithdrawalRuleVersionResponse(
        String id,
        String purposeCode,
        String versionTag,
        CpfRuleStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String approvalReference,
        String changeReason,
        String createdBy,
        String verifiedBy,
        String approvedBy,
        Instant approvedAt,
        int minServiceMonths,
        boolean includePreviousService,
        boolean allowBreakInService,
        CpfFrequencyScope frequencyScope,
        int maxOccurrences,
        int maxActiveConcurrency,
        BigDecimal balanceRetentionPct,
        CpfRepaymentCreditMethod repaymentCreditMethod,
        Integer minTenureMonths,
        Integer maxTenureMonths,
        Integer defaultTenureMonths,
        BigDecimal interestRateAnnual,
        String interestMethod,
        String payrollCapType,
        String taxRuleReference,
        Integer taxServiceThresholdMonths,
        String workflowDefinitionCode
) {
    public static CpfWithdrawalRuleVersionResponse from(CpfWithdrawalRuleVersion v, CpfWithdrawalRuleDetail d) {
        return new CpfWithdrawalRuleVersionResponse(
                v.getId().toString(), v.getPurpose().getCode(), v.getVersionTag(), v.getStatus(), v.getEffectiveFrom(), v.getEffectiveTo(),
                v.getApprovalReference(), v.getChangeReason(), v.getCreatedBy(), v.getVerifiedBy(), v.getApprovedBy(), v.getApprovedAt(),
                d.getMinServiceMonths(), d.isIncludePreviousService(), d.isAllowBreakInService(), d.getFrequencyScope(),
                d.getMaxOccurrences(), d.getMaxActiveConcurrency(), d.getBalanceRetentionPct(), d.getRepaymentCreditMethod(),
                d.getMinTenureMonths(), d.getMaxTenureMonths(), d.getDefaultTenureMonths(), d.getInterestRateAnnual(), d.getInterestMethod(),
                d.getPayrollCapType(), d.getTaxRuleReference(), d.getTaxServiceThresholdMonths(), d.getWorkflowDefinitionCode());
    }
}
