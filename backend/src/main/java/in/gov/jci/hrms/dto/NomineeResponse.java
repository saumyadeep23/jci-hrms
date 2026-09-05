package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.EmployeeNominee;

import java.math.BigDecimal;
import java.time.Instant;

public record NomineeResponse(
        Long id,
        Long employeeId,
        String name,
        String relationship,
        BigDecimal sharePercentage,
        String nomineeFor,
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
                nominee.getCreatedAt(),
                nominee.getUpdatedAt()
        );
    }
}
