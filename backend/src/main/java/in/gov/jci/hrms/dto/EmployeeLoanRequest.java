package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeLoanRequest(
        @NotNull Long loanTypeId,
        @NotNull Long employeeId,
        @NotBlank @Size(max = 50) String loanAccountNumber,
        @NotNull @Positive BigDecimal principalAmount,
        @NotNull @Positive Integer totalInstallments,
        @NotNull LocalDate sanctionDate
) {
}
