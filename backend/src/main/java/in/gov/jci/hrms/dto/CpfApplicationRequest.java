package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /api/v1/payroll/trust/withdrawal-applications - CpfApplicationService.apply(). submittedDocuments
 * is validated against this purpose's own CpfRuleDocument mandatory-document list before the application
 * is created (Part 29) - null/empty is only accepted when the resolved rule has no mandatory documents.
 */
public record CpfApplicationRequest(
        @NotBlank String employeeCode,
        @NotBlank String purposeCode,
        @NotNull @Positive BigDecimal appliedAmount,
        BigDecimal basicPlusDa,
        BigDecimal propertyCost,
        BigDecimal payrollDeductionCapacity,
        List<CpfApplicationDocumentSubmission> submittedDocuments,
        /** Feeds the OUTSTANDING_LOAN ceiling metric - only HOUSING_LOAN_REPAYMENT's rule reads this today; ignored by every other purpose. */
        BigDecimal outstandingLoan
) {
}
