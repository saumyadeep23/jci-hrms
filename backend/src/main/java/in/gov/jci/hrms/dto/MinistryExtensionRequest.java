package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST /api/v1/employees/:id/superannuation/ministry-extension. extendedUptoDate
 * is accepted for API-contract completeness but not trusted as-is: the
 * underlying sp_apply_director_ministry_extension procedure always
 * (re)computes the extended date itself as the employee's 60th-birthday
 * last-day-of-month, the same rule fn_calculate_jci_superannuation_date
 * uses - see SuperannuationExtensionService. The response returns whatever
 * date the procedure actually applied. remarks is likewise accepted for
 * API-contract completeness but not persisted - employee_superannuation_details
 * has no remarks column and sp_apply_director_ministry_extension's
 * signature doesn't take one.
 */
public record MinistryExtensionRequest(
        @NotNull @Size(max = 100) String orderNumber,
        @NotNull LocalDate orderDate,
        LocalDate extendedUptoDate,
        @Size(max = 500) String remarks
) {
}
