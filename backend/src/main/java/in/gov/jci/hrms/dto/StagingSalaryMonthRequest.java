package in.gov.jci.hrms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record StagingSalaryMonthRequest(
        @NotBlank @Size(max = 50) String employeeCode,
        @NotNull Integer salaryYear,
        @NotNull @Min(1) @Max(12) Integer salaryMonth,
        @NotNull BigDecimal basicPay,
        @NotNull BigDecimal grossEarnings,
        @NotNull BigDecimal totalDeductions,
        @NotNull BigDecimal netPay,
        @NotEmpty List<@Valid StagingSalaryHeadRequest> heads
) {
}
