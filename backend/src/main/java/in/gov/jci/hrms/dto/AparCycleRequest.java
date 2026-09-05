package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AparCycleRequest(
        @NotBlank @Size(max = 20) String cycleYear,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
