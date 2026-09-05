package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;

import java.time.Instant;
import java.time.LocalDate;

public record HolidayResponse(
        Long id,
        LocalDate holidayDate,
        String name,
        HolidayType holidayType,
        String state,
        Instant createdAt,
        Instant updatedAt
) {
    public static HolidayResponse from(Holiday holiday) {
        return new HolidayResponse(
                holiday.getId(),
                holiday.getHolidayDate(),
                holiday.getName(),
                holiday.getHolidayType(),
                holiday.getState(),
                holiday.getCreatedAt(),
                holiday.getUpdatedAt()
        );
    }
}
