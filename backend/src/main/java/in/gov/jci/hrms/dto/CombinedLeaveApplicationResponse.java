package in.gov.jci.hrms.dto;

import java.util.UUID;

public record CombinedLeaveApplicationResponse(
        UUID groupApplicationId,
        LeaveApplicationResponse clApplication,
        LeaveApplicationResponse rhApplication
) {
}
