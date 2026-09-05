package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveSession;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Deliberately has no totalDays - the whole point of a preview is to find out what totalDays should be. */
public record LeaveApplicationPreviewRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LeaveSession leaveSession
) {
}
