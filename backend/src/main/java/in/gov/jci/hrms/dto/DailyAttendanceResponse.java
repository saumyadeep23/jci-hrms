package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.DailyAttendance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyAttendanceResponse(
        Long id,
        Long employeeId,
        String employeeCode,
        LocalDate attendanceDate,
        AttendanceStatus status,
        Instant inTime,
        Instant outTime,
        BigDecimal totalWorkingHours,
        Instant createdAt,
        Instant updatedAt
) {
    public static DailyAttendanceResponse from(DailyAttendance attendance) {
        return new DailyAttendanceResponse(
                attendance.getId(),
                attendance.getEmployee().getId(),
                attendance.getEmployee().getEmployeeCode(),
                attendance.getAttendanceDate(),
                attendance.getStatus(),
                attendance.getInTime(),
                attendance.getOutTime(),
                attendance.getTotalWorkingHours(),
                attendance.getCreatedAt(),
                attendance.getUpdatedAt()
        );
    }
}
