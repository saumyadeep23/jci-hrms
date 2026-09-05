package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.PostMaster;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LeaveApplicationResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        Long leaveTypeId,
        String leaveTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalDays,
        String reason,
        LeaveApplicationStatus status,
        LeaveSession leaveSession,
        Long approverPostId,
        String approverPostTitle,
        Long approverEmployeeId,
        String approverEmployeeCode,
        UUID groupApplicationId,
        Long rhEntryId,
        BigDecimal debitedEnjoyableDays,
        BigDecimal debitedEncashableDays,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeaveApplicationResponse from(LeaveApplication application) {
        PostMaster approverPost = application.getApproverPost();
        Employee approverEmployee = application.getApproverEmployee();

        return new LeaveApplicationResponse(
                application.getId(),
                application.getEmployee().getId(),
                application.getEmployee().getEmployeeCode(),
                application.getLeaveType().getId(),
                application.getLeaveType().getCode(),
                application.getStartDate(),
                application.getEndDate(),
                application.getTotalDays(),
                application.getReason(),
                application.getStatus(),
                application.getLeaveSession(),
                approverPost != null ? approverPost.getId() : null,
                approverPost != null ? approverPost.getTitle() : null,
                approverEmployee != null ? approverEmployee.getId() : null,
                approverEmployee != null ? approverEmployee.getEmployeeCode() : null,
                application.getGroupApplicationId(),
                application.getRhEntry() != null ? application.getRhEntry().getId() : null,
                application.getDebitedEnjoyableDays(),
                application.getDebitedEncashableDays(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
