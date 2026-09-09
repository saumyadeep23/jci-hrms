package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.DaProjectionEmployee;

import java.math.BigDecimal;
import java.util.List;

/** One employee's roll-up within a DaProjectionBatch - EmployeeArrearScheduleTable's parent row, with the month-by-month audit trail nested for its expand/accordion toggle. */
public record IdaProjectionEmployeeResponse(
        Long employeeId,
        String employeeCode,
        String fullName,
        String designation,
        String pensionScheme,
        int totalMonthsCount,
        BigDecimal totalGrossArrears,
        BigDecimal totalEmployeeCpfArrear,
        BigDecimal totalEmployerJcpfArrear,
        BigDecimal totalEmployeeNpsArrear,
        BigDecimal totalEmployerNpsArrear,
        BigDecimal totalLeaveEncashmentArrear,
        BigDecimal totalNetArrearPayable,
        BigDecimal totalEmployerCost,
        List<IdaProjectionMonthlyBreakupResponse> monthlyBreakups
) {
    public static IdaProjectionEmployeeResponse from(DaProjectionEmployee pe, List<IdaProjectionMonthlyBreakupResponse> monthlyBreakups) {
        return new IdaProjectionEmployeeResponse(
                pe.getEmployee().getId(),
                pe.getEmployee().getEmployeeCode(),
                pe.getEmployee().getFullName(),
                pe.getEmployee().getDesignation() != null ? pe.getEmployee().getDesignation().getTitle() : null,
                pe.getPensionScheme(),
                pe.getTotalMonthsCount(),
                pe.getTotalGrossArrears(),
                pe.getTotalEmployeeCpfArrear(),
                pe.getTotalEmployerJcpfArrear(),
                pe.getTotalEmployeeNpsArrear(),
                pe.getTotalEmployerNpsArrear(),
                pe.getTotalLeaveEncashmentArrear(),
                pe.getTotalNetArrearPayable(),
                pe.getTotalEmployerCost(),
                monthlyBreakups
        );
    }
}
