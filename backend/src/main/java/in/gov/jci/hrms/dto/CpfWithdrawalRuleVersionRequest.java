package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfFrequencyScope;
import in.gov.jci.hrms.entity.CpfRepaymentCreditMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * POST /api/v1/payroll/trust/withdrawal-rules - CpfWithdrawalRuleService.createDraft(). purposeCode groups
 * this with every other version of "the same" rule (all future versions for that purpose) - see
 * CpfWithdrawalRuleVersion's own javadoc. Creating a new version never edits a prior one.
 */
public record CpfWithdrawalRuleVersionRequest(
        @NotBlank String purposeCode,
        @NotBlank String versionTag,
        @NotNull LocalDate effectiveFrom,
        @NotBlank String changeReason,

        int minServiceMonths,
        boolean includePreviousService,
        boolean allowBreakInService,
        @NotNull CpfFrequencyScope frequencyScope,
        int maxOccurrences,
        int maxActiveConcurrency,
        BigDecimal balanceRetentionPct,
        @NotNull CpfRepaymentCreditMethod repaymentCreditMethod,

        Integer minTenureMonths,
        Integer maxTenureMonths,
        Integer defaultTenureMonths,
        BigDecimal interestRateAnnual,
        String interestMethod,
        Boolean allowPrepayment,
        Boolean allowConversion,

        String payrollCapType,
        String taxRuleReference,
        Integer taxServiceThresholdMonths,
        String workflowDefinitionCode,

        List<CpfRuleHeadConfig> heads,
        List<CpfRuleCeilingConfig> ceilings,
        List<CpfRuleDocumentConfig> documents
) {
}
