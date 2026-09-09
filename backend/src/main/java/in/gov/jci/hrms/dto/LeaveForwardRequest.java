package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;

public record LeaveForwardRequest(
        @NotNull Long forwardedToEmployeeId,
        String remarks
) {
}
