package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.Gender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DependentRequest(
        @NotNull Long employeeId,
        @NotBlank @Size(max = 150) String name,
        @NotNull FamilyRelationshipType relationship,
        @Past LocalDate dateOfBirth,
        @NotNull Boolean isDependent,
        @NotNull Boolean isCoveredMedical,
        Gender gender,
        @NotNull Boolean isDivyang,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal disabilityPercentage,
        @NotNull Boolean isMultipleBirthSecondDelivery
) {
}
