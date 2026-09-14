package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CpfLoanRecoveryPolicyRequest(
        @NotNull String commencementMode, @Min(1) int principalInstallmentsPerInterestInstallment,
        @NotNull LocalDate effectiveFrom, String remarks
) {
}
