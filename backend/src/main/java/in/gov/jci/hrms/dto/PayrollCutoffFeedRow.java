package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One employee's row in the 25th Payroll Cutoff Disbursal Feed - ALMS Reporting Workbench. */
public record PayrollCutoffFeedRow(
        Long employeeId,
        String employeeCode,
        String employeeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        int totalCycleDays,
        BigDecimal payableDays,
        BigDecimal lwpDays,
        int absentDays,
        BigDecimal penaltyDays,
        BigDecimal approvedInServiceElDays,
        String financeOrderRef,
        boolean isLocked
) {
}
