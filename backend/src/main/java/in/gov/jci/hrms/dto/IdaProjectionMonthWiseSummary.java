package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** One calendar month's org-wide totals across every impacted employee in a DaProjectionBatch - aggregated from DaProjectionMonthlyBreakup rows. */
public record IdaProjectionMonthWiseSummary(
        int salMonth,
        int salYear,
        String monthLabel,
        BigDecimal totalDeltaDa,
        BigDecimal totalEmployeeCpfArrear,
        BigDecimal totalEmployerJcpfArrear,
        BigDecimal totalEmployeeNpsArrear,
        BigDecimal totalEmployerNpsArrear,
        BigDecimal totalNetMonthlyArrear,
        BigDecimal totalEmployerCostMonthly
) {
}
