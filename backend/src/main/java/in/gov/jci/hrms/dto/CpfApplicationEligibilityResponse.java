package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

public record CpfApplicationEligibilityResponse(
        String purposeCode,
        String ruleVersionId,
        String ruleVersionTag,
        boolean eligible,
        String eligibilityReason,
        BigDecimal totalEligibleBalance,
        BigDecimal eligibleAmount,
        boolean serviceEligible,
        String serviceEligibilityReason,
        boolean frequencyEligible,
        String frequencyReason,
        List<String> calculationTrace,
        /** Part 29 - this purpose's own required-document list (mandatory/optional, allowed MIME types, max size), so the applicant knows what to upload before submitting. */
        List<CpfRuleDocumentConfig> requiredDocuments,
        /** Task 4 - each configured ceiling component's source metric and calculated value, structured
         * (not parsed out of calculationTrace) so the Simulator/Apply-for-Loan UI can render them as real
         * fields. Always populated, even before real context values are known (components then show 0). */
        List<CeilingComponentResponse> ceilingComponents,
        /** Task 4 - the configured debit priority order for this purpose (CpfRuleHeadEligibility), with a
         * projected per-head debit amount once an eligible/requested amount is known. Never hardcoded
         * "VPF then EE" - this is exactly resolveEligibleHeadsOrdered()'s own configured order. */
        List<HeadAllocationResponse> headAllocation,
        /** Task 4 - non-mutating repayment preview, populated only when the purpose is refundable, the
         * rule configures interest, and the applicant is eligible; null otherwise (e.g. non-refundable
         * withdrawals never carry a repayment schedule). */
        RepaymentPreviewResponse repaymentPreview,
        /** Task 4 Part 6 - whether this purpose's rule carries a repayment schedule at all (refundable type
         * + interest configured), so the wizard knows whether to show a tenure step before repaymentPreview
         * can be non-null. Mirrors buildRepaymentPreview's own carriesRepaymentSchedule check exactly. */
        boolean carriesRepaymentSchedule,
        /** Task 4 - the configured tenure bounds/default (CpfWithdrawalRuleDetail), so the wizard can render
         * a real tenure selector (min/max/default) instead of guessing a valid value by trial and error.
         * Null when carriesRepaymentSchedule is false. */
        Integer minTenureMonths,
        Integer maxTenureMonths,
        Integer defaultTenureMonths
) {
}
