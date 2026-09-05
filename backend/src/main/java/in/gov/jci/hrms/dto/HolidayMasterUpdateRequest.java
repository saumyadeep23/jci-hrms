package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Edits one already-persisted (single-state) Holiday row - stateCode "ALL" re-scopes it to national. To change a holiday's state coverage, delete and re-create via the multi-state create endpoint instead of trying to fan an update out into multiple rows. */
public record HolidayMasterUpdateRequest(
        @NotBlank @Size(max = 150) String holidayName,
        @NotNull LocalDate holidayDate,
        @NotNull HolidayType holidayType,
        @NotBlank String stateCode,
        String description
) {
}
