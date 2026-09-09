package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CeaEligibilityStatus;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.Gender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DependentResponse(
        Long id,
        Long employeeId,
        String name,
        FamilyRelationshipType relationship,
        LocalDate dateOfBirth,
        boolean isDependent,
        boolean isCoveredMedical,
        Gender gender,
        boolean isDivyang,
        BigDecimal disabilityPercentage,
        boolean isMultipleBirthSecondDelivery,
        /** Computed by EmployeeDependent.computeCeaEligibility() - null when relationship isn't SON/DAUGHTER. */
        CeaEligibilityStatus ceaEligibility,
        Instant createdAt,
        Instant updatedAt
) {
    public static DependentResponse from(EmployeeDependent dependent) {
        return new DependentResponse(
                dependent.getId(),
                dependent.getEmployee().getId(),
                dependent.getName(),
                dependent.getRelationship(),
                dependent.getDateOfBirth(),
                dependent.isDependent(),
                dependent.isCoveredMedical(),
                dependent.getGender(),
                dependent.isDivyang(),
                dependent.getDisabilityPercentage(),
                dependent.isMultipleBirthSecondDelivery(),
                dependent.computeCeaEligibility(),
                dependent.getCreatedAt(),
                dependent.getUpdatedAt()
        );
    }
}
