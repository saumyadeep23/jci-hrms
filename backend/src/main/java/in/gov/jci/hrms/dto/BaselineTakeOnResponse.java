package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveBaselineInitialization;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BaselineTakeOnResponse(
        UUID baselineId,
        Long employeeId,
        String employeeCode,
        Long leaveTypeId,
        String leaveTypeCode,
        LocalDate asOnDate,
        BigDecimal openingBalance,
        BigDecimal openingEncashableEl,
        BigDecimal openingEnjoyableEl,
        String physicalServiceBookFolio,
        String verificationOrderRef,
        Long verifiedByEmployeeId,
        boolean locked,
        Instant createdAt
) {
    public static BaselineTakeOnResponse from(LeaveBaselineInitialization entity) {
        return new BaselineTakeOnResponse(
                entity.getBaselineId(),
                entity.getEmployee().getId(),
                entity.getEmployee().getEmployeeCode(),
                entity.getLeaveType().getId(),
                entity.getLeaveType().getCode(),
                entity.getAsOnDate(),
                entity.getOpeningBalance(),
                entity.getOpeningEncashableEl(),
                entity.getOpeningEnjoyableEl(),
                entity.getPhysicalServiceBookFolio(),
                entity.getVerificationOrderRef(),
                entity.getVerifiedBy() != null ? entity.getVerifiedBy().getId() : null,
                entity.isLocked(),
                entity.getCreatedAt());
    }
}
