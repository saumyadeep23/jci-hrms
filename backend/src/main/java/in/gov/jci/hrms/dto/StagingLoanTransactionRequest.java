package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StagingLoanTransactionRequest(
        @NotNull LocalDate paymentDate,
        @NotNull BigDecimal principalComponent,
        @NotNull BigDecimal interestComponent,
        @NotNull BigDecimal totalAmount
) {
}
