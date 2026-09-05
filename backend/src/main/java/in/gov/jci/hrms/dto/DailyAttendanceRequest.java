package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record DailyAttendanceRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate attendanceDate,
        @NotNull AttendanceStatus status,
        Instant inTime,
        Instant outTime
) {
}
