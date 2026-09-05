package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StagingLeaveBalanceRequest(
        @NotBlank @Size(max = 50) String employeeCode,
        @NotBlank @Size(max = 20) String leaveTypeCode,
        @NotNull BigDecimal openingBalance,
        @NotNull LocalDate asOnDate
) {
}
