package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NonRefundableWithdrawalRequest(
        @NotNull Long employeeId,
        @NotNull @DecimalMin("0.0") BigDecimal empBucketAmount,
        @NotNull @DecimalMin("0.0") BigDecimal erBucketAmount,
        @NotNull @DecimalMin("0.0") BigDecimal vpfBucketAmount,
        @NotNull LocalDate sanctionDate
) {
}
