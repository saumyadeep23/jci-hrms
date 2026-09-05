package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.RepaymentSource;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanRepaymentRequest(
        @NotNull RepaymentSource repaymentSource,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate paymentDate,
        Long payrollRunId,
        @Size(max = 100) String transactionReference
) {
}
