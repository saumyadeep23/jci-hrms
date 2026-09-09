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

/**
 * One row of the unified Family Register grid, nested in EmployeeFamilyNomineeCompositeRequest.
 * clientKey is a stable identifier the frontend assigns to this row (its own real id as a string for
 * an existing dependent, or a UI-generated temporary key such as "new-1" for a row added in the same
 * edit session) - CompositeNomineeEntry.dependentClientKey references it, which is what lets a
 * brand-new family member be selected as a nominee and saved in the same "Save Changes" submission,
 * before that dependent has a real database id. id is null for a new row, non-null for an existing
 * one being edited; any existing dependent NOT present in the submitted list is soft-deleted - the
 * grid is the full source of truth for the employee's Family Register on every save.
 */
public record CompositeDependentEntry(
        @NotBlank String clientKey,
        Long id,
        @NotBlank @Size(max = 150) String name,
        @NotNull FamilyRelationshipType relationship,
        @NotNull @Past LocalDate dateOfBirth,
        Gender gender,
        @NotNull Boolean isDependent,
        @NotNull Boolean isCoveredMedical,
        @NotNull Boolean isDivyang,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal disabilityPercentage,
        @NotNull Boolean isMultipleBirthSecondDelivery
) {
}
