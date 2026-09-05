package in.gov.jci.hrms.dto;

import java.time.LocalDate;

/** Result of applying sp_apply_director_ministry_extension - the actually-persisted values, not an echo of the request. */
public record SuperannuationExtensionResponse(
        Long employeeId,
        boolean isMinistryExtended,
        String ministryExtensionOrderNo,
        LocalDate ministryExtensionOrderDate,
        LocalDate ministryExtendedUpto,
        LocalDate superannuationDate,
        String calculationBasis
) {
}
