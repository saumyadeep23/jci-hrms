package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeNominee;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.NominationType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record NomineeResponse(
        Long id,
        Long employeeId,
        String name,
        FamilyRelationshipType relationship,
        BigDecimal sharePercentage,
        NominationType nomineeFor,
        Long dependentId,
        /** Read-only, denormalized from the linked Family Register row for display - not stored on the nominee itself. */
        LocalDate dependentDateOfBirth,
        Instant createdAt,
        Instant updatedAt
) {
    public static NomineeResponse from(EmployeeNominee nominee) {
        return new NomineeResponse(
                nominee.getId(),
                nominee.getEmployee().getId(),
                nominee.getName(),
                nominee.getRelationship(),
                nominee.getSharePercentage(),
                nominee.getNomineeFor(),
                nominee.getDependent() != null ? nominee.getDependent().getId() : null,
                nominee.getDependent() != null ? nominee.getDependent().getDateOfBirth() : null,
                nominee.getCreatedAt(),
                nominee.getUpdatedAt()
        );
    }
}
