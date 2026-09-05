package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RefundableLoanDiversionRequest(
        @NotNull Long employeeId,
        @NotNull @Positive BigDecimal amount,
        @NotNull Long linkedLoanId,
        @NotNull LocalDate sanctionDate
) {
}
