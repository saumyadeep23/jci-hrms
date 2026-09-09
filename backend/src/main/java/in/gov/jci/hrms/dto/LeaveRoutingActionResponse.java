package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveActionType;
import in.gov.jci.hrms.entity.LeaveApplicationAction;

import java.time.Instant;

public record LeaveRoutingActionResponse(
        Long id,
        LeaveActionType actionType,
        Long actionByEmployeeId,
        String actionByName,
        String actionByDesignation,
        Long forwardedToEmployeeId,
        String forwardedToName,
        String remarks,
        Instant createdAt
) {
    public static LeaveRoutingActionResponse from(LeaveApplicationAction entity) {
        Employee actionBy = entity.getActionBy();
        Employee forwardedTo = entity.getForwardedTo();
        return new LeaveRoutingActionResponse(
                entity.getId(),
                entity.getActionType(),
                actionBy.getId(),
                actionBy.getFullName(),
                actionBy.getDesignation() != null ? actionBy.getDesignation().getTitle() : null,
                forwardedTo != null ? forwardedTo.getId() : null,
                forwardedTo != null ? forwardedTo.getFullName() : null,
                entity.getRemarks(),
                entity.getCreatedAt());
    }
}
