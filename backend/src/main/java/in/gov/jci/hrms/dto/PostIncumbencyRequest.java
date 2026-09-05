package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AssignmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PostIncumbencyRequest(
        @NotNull Long postId,
        @NotNull Long employeeId,
        @NotNull AssignmentType assignmentType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Size(max = 100) String orderReference
) {
}
