package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * POST /api/leave-applications/combined - a CL+RH pair submitted together
 * (CombinedLeaveApplicationService): either a same-day split (clSession +
 * rhSession opposite halves, clDate == rhDate) or a contiguous multi-day
 * prefix/suffix (rhDate immediately before/after the CL span). rhHolidayId
 * is the specific holidays row (holiday_type = RESTRICTED) being observed.
 */
public record CombinedLeaveApplicationRequest(
        @NotNull Long employeeId,
        @NotNull Long clLeaveTypeId,
        @NotNull LocalDate clStartDate,
        @NotNull LocalDate clEndDate,
        LeaveSession clSession,
        @NotNull Long rhLeaveTypeId,
        @NotNull LocalDate rhDate,
        LeaveSession rhSession,
        @NotNull Long rhHolidayId,
        @NotBlank String reason
) {
}
