package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** clearedByUserId is never client-supplied - the controller resolves it from the authenticated principal (SecurityUtils.currentEmployeeId). */
public record ExitClearanceItemUpdateRequest(
        @NotNull ExitClearanceItemStatus status,
        BigDecimal duesRecoveryAmount,
        String remarks
) {
}
