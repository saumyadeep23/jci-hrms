package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * loanTypeCode/loanStatus are plain strings, not typed enums, so an
 * unrecognized legacy code produces a row-level rejection (FR-MIG.1-style
 * non-blocking validation) rather than a 400 that fails the whole batch.
 */
public record StagingLoanRequest(
        @NotBlank @Size(max = 50) String employeeCode,
        @NotBlank @Size(max = 20) String loanTypeCode,
        @NotBlank @Size(max = 50) String loanAccountNumber,
        @NotNull BigDecimal principalAmount,
        @NotNull BigDecimal interestRate,
        @NotNull LocalDate sanctionDate,
        LocalDate disbursementDate,
        @NotNull Integer tenureMonths,
        BigDecimal outstandingPrincipal,
        Integer remainingInstallments,
        @NotBlank @Size(max = 20) String loanStatus,
        List<@Valid StagingLoanTransactionRequest> transactions
) {
}
