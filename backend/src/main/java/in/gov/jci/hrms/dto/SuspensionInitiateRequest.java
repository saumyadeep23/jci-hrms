package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SuspensionInitiateRequest(
        @NotNull Long employeeId,
        @NotBlank String suspensionOrderNo,
        @NotNull LocalDate suspensionOrderDate,
        @NotNull LocalDate effectiveFrom,
        @NotBlank String hqStation
) {
}
