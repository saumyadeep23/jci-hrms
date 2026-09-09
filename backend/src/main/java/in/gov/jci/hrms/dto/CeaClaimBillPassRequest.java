package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CeaClaimBillPassRequest(
        @NotNull @DecimalMin(value = "0.01", message = "must be positive") BigDecimal passedAmount,
        @NotBlank @Size(max = 100) String billNo,
        @NotNull LocalDate billDate,
        @NotBlank @Size(max = 100) String sanctionOrderNo,
        @NotNull LocalDate sanctionDate
) {
}
