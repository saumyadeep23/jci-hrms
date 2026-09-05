package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record ShiftMasterRequest(
        @NotBlank @Size(max = 20) String shiftCode,
        @NotBlank @Size(max = 100) String shiftName,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @PositiveOrZero Integer gracePeriodMinutes,
        @NotNull Boolean crossesMidnight,
        /** Minimum worked minutes to count as a full/half day - optional; left null for a shift (e.g. a watchmen rotation) with no such policy defined yet. */
        @PositiveOrZero Integer fullDayMinutes,
        @PositiveOrZero Integer halfDayMinutes,
        /** 'HEAD_OFFICE' / 'REGIONAL_OFFICE' / 'DPC', or null - informational tag only, see ShiftMaster's javadoc. */
        @Size(max = 20) String applicableOfficeType,
        @NotNull Boolean active
) {
}
