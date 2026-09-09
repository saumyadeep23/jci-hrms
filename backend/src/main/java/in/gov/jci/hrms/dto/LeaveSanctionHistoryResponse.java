package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One row of the Sanction History register - forwardedByName is the last RECOMMEND_FORWARD actor before the final decision, null if the application was decided without ever being forwarded. */
public record LeaveSanctionHistoryResponse(
        Long id,
        String employeeCode,
        String employeeName,
        String leaveTypeCode,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalDays,
        String forwardedByName,
        String sanctionedByName,
        Instant decidedAt,
        LeaveApplicationStatus status
) {
    public static LeaveSanctionHistoryResponse from(LeaveApplication application, String forwardedByName, String sanctionedByName, Instant decidedAt) {
        Employee employee = application.getEmployee();
        return new LeaveSanctionHistoryResponse(
                application.getId(),
                employee.getEmployeeCode(),
                employee.getFullName(),
                application.getLeaveType().getCode(),
                application.getStartDate(),
                application.getEndDate(),
                application.getTotalDays(),
                forwardedByName,
                sanctionedByName,
                decidedAt,
                application.getStatus());
    }
}
