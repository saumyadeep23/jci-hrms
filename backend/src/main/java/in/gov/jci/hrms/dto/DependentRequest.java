package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DependentRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String relationship,
        @Past LocalDate dateOfBirth,
        @NotNull Boolean isDependent,
        @NotNull Boolean isCoveredMedical
) {
}
