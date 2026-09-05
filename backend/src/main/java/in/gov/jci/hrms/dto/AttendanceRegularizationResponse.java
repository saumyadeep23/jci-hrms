package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceRegularizationApplication;
import in.gov.jci.hrms.entity.RegularizationReasonCode;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceRegularizationResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        LocalDate attendanceDate,
        Long dailyAttendanceId,
        RegularizationReasonCode reasonCode,
        String remarks,
        Instant correctedInTime,
        Instant correctedOutTime,
        ApprovalStatus approvalStatus,
        Long designatedApproverId,
        Instant approvedAt,
        String approverRemarks,
        Instant createdAt
) {
    public static AttendanceRegularizationResponse from(AttendanceRegularizationApplication entity) {
        return new AttendanceRegularizationResponse(
                entity.getId(),
                entity.getEmployee().getId(),
                entity.getEmployee().getEmployeeCode(),
                entity.getAttendanceDate(),
                entity.getDailyAttendance() != null ? entity.getDailyAttendance().getId() : null,
                entity.getReasonCode(),
                entity.getRemarks(),
                entity.getCorrectedInTime(),
                entity.getCorrectedOutTime(),
                entity.getApprovalStatus(),
                entity.getDesignatedApprover() != null ? entity.getDesignatedApprover().getId() : null,
                entity.getApprovedAt(),
                entity.getApproverRemarks(),
                entity.getCreatedAt());
    }
}
