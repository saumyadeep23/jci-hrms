package in.gov.jci.hrms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response for HolidayController's GET /api/holidays/my-calendar - the
 * union of national/CENTRAL gazetted holidays, the caller's state-specific
 * gazetted holidays, and restricted holidays available at their location,
 * for one calendar month.
 */
public record HolidayCalendarResponse(
        String officeLabel,
        String state,
        List<HolidayResponse> holidays,
        LocalDate upcomingHolidayDate,
        String upcomingHolidayName
) {
}
