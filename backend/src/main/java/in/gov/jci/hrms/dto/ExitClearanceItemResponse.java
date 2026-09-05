package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ExitClearanceDepartment;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ExitClearanceItemResponse(
        Long id,
        ExitClearanceDepartment departmentCode,
        ExitClearanceItemStatus status,
        BigDecimal duesRecoveryAmount,
        String remarks,
        Long clearedByUserId,
        Instant clearedAt
) {
    public static ExitClearanceItemResponse from(ExitClearanceItem item) {
        return new ExitClearanceItemResponse(item.getId(), item.getDepartmentCode(), item.getStatus(),
                item.getDuesRecoveryAmount(), item.getRemarks(), item.getClearedByUserId(), item.getClearedAt());
    }
}
