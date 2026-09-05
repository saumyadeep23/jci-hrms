package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.HolidayType;

import java.time.LocalDate;

/**
 * Holiday & RH Publisher's per-row view - one row per persisted Holiday
 * (each still single-state internally; a multi-state create/CSV import
 * fans out into one Holiday row per state, see HolidayMasterService). Wire
 * format for holidayDate stays ISO (LocalDate's default Jackson
 * serialization) - matching every other date field in this codebase; the
 * dd-MM-yyyy display/input format the task's frontend section asks for is
 * produced entirely client-side via the existing DateField component/
 * formatDate() utility, not by this DTO.
 */
public record HolidayMasterRow(
        Long id,
        LocalDate holidayDate,
        String holidayName,
        HolidayType holidayType,
        boolean isRestricted,
        String stateCode,
        String stateName,
        String description
) {
}
