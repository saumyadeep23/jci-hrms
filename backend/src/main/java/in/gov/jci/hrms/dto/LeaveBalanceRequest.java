package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LeaveBalanceRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull Integer year,
        @NotNull @DecimalMin("0.0") BigDecimal creditedDays
) {
}
