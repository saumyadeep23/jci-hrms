package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** Result of CpfRateResolutionService.resolveStatutoryRate() - the rate to apply plus which FY it actually came from. */
public record CpfResolvedRateDto(
        BigDecimal baseRate,
        BigDecimal loanRate,
        String requestedFinYear,
        String effectiveFinYear,
        boolean isProvisional
) {
}
