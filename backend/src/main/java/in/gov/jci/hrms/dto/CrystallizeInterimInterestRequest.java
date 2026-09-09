package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** POST /api/v1/payroll/trust/settlements/crystallize-interest - CpfInterestComputationService.crystallizeInterimInterest(). */
public record CrystallizeInterimInterestRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate settlementDate,
        @NotBlank String settlementType
) {
}
