package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * stateCodes fans out into one Holiday row per code within a single
 * transaction (HolidayMasterService.create) - ["ALL"] (or omitted/absent
 * from every active code) persists one national row instead. No separate
 * isRestricted flag - the originating task spec listed both holidayType and
 * isRestricted, but the latter is fully redundant with
 * holidayType == RESTRICTED (and could otherwise contradict it), so this
 * DTO only carries holidayType and HolidayMasterRow derives isRestricted
 * for display.
 */
public record HolidayMasterCreateRequest(
        @NotBlank @Size(max = 150) String holidayName,
        @NotNull LocalDate holidayDate,
        @NotNull HolidayType holidayType,
        @NotEmpty List<String> stateCodes,
        String description
) {
}
