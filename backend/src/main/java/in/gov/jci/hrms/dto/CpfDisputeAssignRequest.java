package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

public record CpfDisputeAssignRequest(@NotNull Long assigneeEmployeeId, @NotNull Long expectedVersion) {
}
