package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.RegularizationReasonCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/** Employee-submitted regularization request for one attendance_date - approval is a separate HoD action (AttendanceRegularizationController.approve). */
public record AttendanceRegularizationRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate attendanceDate,
        @NotNull RegularizationReasonCode reasonCode,
        @Size(max = 1000) String remarks,
        @NotNull Instant correctedInTime,
        @NotNull Instant correctedOutTime
) {
}
