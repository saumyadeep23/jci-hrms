package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DaProjectionMonthlyBreakup;

import java.math.BigDecimal;

/** One employee's one retro (or current-open) month - EmployeeArrearScheduleTable's nested accordion row. */
public record IdaProjectionMonthlyBreakupResponse(
        int salMonth,
        int salYear,
        String monthLabel,
        int totalDays,
        BigDecimal paidDays,
        BigDecimal actualBasicPay,
        BigDecimal oldDaRate,
        BigDecimal newDaRate,
        BigDecimal deltaDa,
        BigDecimal employeeCpfArrear,
        BigDecimal employerJcpfArrear,
        BigDecimal employeeNpsArrear,
        BigDecimal employerNpsArrear,
        BigDecimal netMonthlyArrear,
        BigDecimal employerCostMonthly
) {
    public static IdaProjectionMonthlyBreakupResponse from(DaProjectionMonthlyBreakup b) {
        return new IdaProjectionMonthlyBreakupResponse(
                b.getSalMonth(), b.getSalYear(), b.getMonthLabel(), b.getTotalDays(), b.getPaidDays(), b.getActualBasicPay(),
                b.getOldDaRate(), b.getNewDaRate(), b.getDeltaDa(), b.getEmployeeCpfArrear(), b.getEmployerJcpfArrear(),
                b.getEmployeeNpsArrear(), b.getEmployerNpsArrear(), b.getNetMonthlyArrear(), b.getEmployerCostMonthly()
        );
    }
}
