package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DeviceApprovalStatus;
import jakarta.validation.constraints.NotNull;

/** PATCH /api/v1/attendance/devices/{id}/status - status must be APPROVED or REVOKED (see RegisteredDeviceService.updateStatus); PENDING_APPROVAL is only ever the auto-assigned default on registration, never a target of this endpoint. */
public record DeviceStatusUpdateRequest(
        @NotNull DeviceApprovalStatus status
) {
}
