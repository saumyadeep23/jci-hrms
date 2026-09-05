package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/** One employee due for superannuation within the report's forecast window - PIMS_SPEC.md dashboard card 6's report. */
public record SuperannuationForecastEntry(
        Long employeeId,
        String employeeCode,
        String employeeName,
        String departmentName,
        String designationTitle,
        LocalDate dateOfBirth,
        LocalDate superannuationDate,
        boolean isBoardDirector,
        String calculationBasis,
        long monthsRemaining
) {
}
