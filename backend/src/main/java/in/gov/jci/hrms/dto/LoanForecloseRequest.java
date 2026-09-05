package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LoanForecloseRequest(
        @NotNull LocalDate paymentDate,
        @Size(max = 100) String transactionReference
) {
}
