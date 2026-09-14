package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** PUT /api/v1/payroll/trust/withdrawal-applications/{id}/sanction - CpfApplicationService.sanction(). tenureMonths only meaningful for a refundable rule (interestMethod configured); ignored otherwise. */
public record CpfApplicationSanctionRequest(
        @NotNull @Positive BigDecimal sanctionedAmount,
        Integer tenureMonths,
        BigDecimal basicPlusDa,
        BigDecimal propertyCost,
        BigDecimal payrollDeductionCapacity,
        BigDecimal outstandingLoan
) {
}
