package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveEntitlementBalance;

import java.math.BigDecimal;

public record LeaveEntitlementBalanceResponse(
        Long id,
        Long employeeId,
        Long leaveTypeId,
        String leaveTypeCode,
        Integer year,
        BigDecimal openingBalance,
        BigDecimal creditedDays,
        BigDecimal availedDays,
        BigDecimal reservedDays,
        BigDecimal encashedDays,
        BigDecimal lapsedDays,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        BigDecimal encashableOpening,
        BigDecimal encashableCredited,
        BigDecimal encashableAvailed,
        BigDecimal encashableReserved,
        BigDecimal encashableEncashed,
        BigDecimal encashableCurrent,
        BigDecimal encashableAvailable,
        BigDecimal enjoyableOpening,
        BigDecimal enjoyableCredited,
        BigDecimal enjoyableAvailed,
        BigDecimal enjoyableReserved,
        BigDecimal enjoyableCurrent,
        BigDecimal enjoyableAvailable
) {
    public static LeaveEntitlementBalanceResponse from(LeaveEntitlementBalance entity) {
        return new LeaveEntitlementBalanceResponse(
                entity.getId(),
                entity.getEmployee().getId(),
                entity.getLeaveType().getId(),
                entity.getLeaveType().getCode(),
                entity.getYear(),
                entity.getOpeningBalance(),
                entity.getCreditedDays(),
                entity.getAvailedDays(),
                entity.getReservedDays(),
                entity.getEncashedDays(),
                entity.getLapsedDays(),
                entity.getCurrentBalance(),
                entity.getAvailableBalance(),
                entity.getEncashableOpening(),
                entity.getEncashableCredited(),
                entity.getEncashableAvailed(),
                entity.getEncashableReserved(),
                entity.getEncashableEncashed(),
                entity.getEncashableCurrent(),
                entity.getEncashableAvailable(),
                entity.getEnjoyableOpening(),
                entity.getEnjoyableCredited(),
                entity.getEnjoyableAvailed(),
                entity.getEnjoyableReserved(),
                entity.getEnjoyableCurrent(),
                entity.getEnjoyableAvailable());
    }
}
