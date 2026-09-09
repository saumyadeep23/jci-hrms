package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcurementAllowanceRequest(
        @NotNull Long designationId,
        @NotNull @DecimalMin(value = "0", message = "must not be negative") BigDecimal monthlyAllowance,
        @NotNull LocalDate effectiveFrom
) {
}
