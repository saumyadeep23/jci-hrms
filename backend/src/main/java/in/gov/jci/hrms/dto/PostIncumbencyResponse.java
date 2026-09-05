package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.PostIncumbency;

import java.time.Instant;
import java.time.LocalDate;

public record PostIncumbencyResponse(
        Long id,
        Long postId,
        String postCode,
        Long employeeId,
        String employeeCode,
        AssignmentType assignmentType,
        LocalDate startDate,
        LocalDate endDate,
        String orderReference,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static PostIncumbencyResponse from(PostIncumbency incumbency) {
        return new PostIncumbencyResponse(
                incumbency.getId(),
                incumbency.getPost().getId(),
                incumbency.getPost().getPostCode(),
                incumbency.getEmployee().getId(),
                incumbency.getEmployee().getEmployeeCode(),
                incumbency.getAssignmentType(),
                incumbency.getStartDate(),
                incumbency.getEndDate(),
                incumbency.getOrderReference(),
                incumbency.isActive(),
                incumbency.getCreatedAt(),
                incumbency.getUpdatedAt()
        );
    }
}
