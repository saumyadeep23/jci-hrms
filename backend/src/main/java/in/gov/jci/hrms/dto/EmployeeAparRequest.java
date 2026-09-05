package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

public record EmployeeAparRequest(
        @NotNull Long aparCycleId,
        @NotNull Long employeeId,
        @NotNull Long reportingOfficerId,
        @NotNull Long reviewingOfficerId,
        @NotNull Long acceptingAuthorityId
) {
}
