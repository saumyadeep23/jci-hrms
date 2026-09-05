package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.LeaveSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveApplicationRequest(
        @NotNull Long employeeId,
        @NotNull Long leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @NotNull @Positive BigDecimal totalDays,
        @NotBlank String reason,
        /** Optional - null/omitted means FULL_DAY. Only CL supports FIRST_HALF/SECOND_HALF (LeaveValidationService enforces that). */
        LeaveSession leaveSession
) {
}
