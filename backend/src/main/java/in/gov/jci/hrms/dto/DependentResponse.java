package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeDependent;

import java.time.Instant;
import java.time.LocalDate;

public record DependentResponse(
        Long id,
        Long employeeId,
        String name,
        String relationship,
        LocalDate dateOfBirth,
        boolean isDependent,
        boolean isCoveredMedical,
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
                dependent.getCreatedAt(),
                dependent.getUpdatedAt()
        );
    }
}
