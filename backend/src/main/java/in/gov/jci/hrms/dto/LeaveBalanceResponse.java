package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveBalance;

import java.math.BigDecimal;
import java.time.Instant;

public record LeaveBalanceResponse(
        Long id,
        Long leaveTypeId,
        String leaveTypeCode,
        String leaveTypeName,
        Integer year,
        BigDecimal creditedDays,
        BigDecimal usedDays,
        BigDecimal reservedDays,
        BigDecimal availableDays,
        Instant updatedAt
) {
    public static LeaveBalanceResponse from(LeaveBalance balance) {
        return new LeaveBalanceResponse(
                balance.getId(),
                balance.getLeaveType().getId(),
                balance.getLeaveType().getCode(),
                balance.getLeaveType().getName(),
                balance.getYear(),
                balance.getCreditedDays(),
                balance.getUsedDays(),
                balance.getReservedDays(),
                balance.getAvailableDays(),
                balance.getUpdatedAt()
        );
    }
}
