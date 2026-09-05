package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** state is optional - null (or "CENTRAL") means the holiday applies nationally/to every location. */
public record HolidayRequest(
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 150) String name,
        @NotNull HolidayType holidayType,
        @Size(max = 100) String state
) {
}
