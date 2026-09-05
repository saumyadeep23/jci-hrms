package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/**
 * GET /api/v1/employees/:id/superannuation/calculation-preview - standard
 * 58-year vs. 60-year/tenure projections, mirroring
 * fn_calculate_jci_superannuation_date's last-day-of-month rule in Java
 * (see SuperannuationExtensionService). regularSuperannuationDate58/
 * directorSuperannuationDate60 are hypothetical "what if" projections from
 * date of birth alone; actualSuperannuationDate/actualCalculationBasis are
 * the DB-authoritative current values from employee_superannuation_details.
 */
public record SuperannuationCalculationPreviewResponse(
        Long employeeId,
        LocalDate dateOfBirth,
        LocalDate regularSuperannuationDate58,
        LocalDate directorSuperannuationDate60,
        LocalDate director5YearTermDate,
        LocalDate actualSuperannuationDate,
        String actualCalculationBasis,
        boolean isBoardDirector,
        boolean isMinistryExtended,
        LocalDate ministryExtendedUpto
) {
}
