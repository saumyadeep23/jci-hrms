package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PayrollRunRequest(
        @NotNull Integer cycleYear,
        @NotNull @Min(1) @Max(12) Integer cycleMonth
) {
}
