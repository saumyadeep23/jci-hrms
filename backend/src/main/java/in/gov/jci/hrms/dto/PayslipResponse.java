package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.Payslip;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PayslipResponse(
        Long id,
        Long payrollRunId,
        Long employeeId,
        String employeeCode,
        BigDecimal basicPay,
        BigDecimal totalEarnings,
        BigDecimal totalDeductions,
        BigDecimal employerContributions,
        BigDecimal netPay,
        BigDecimal lopDays,
        boolean isHold,
        Instant createdAt,
        List<PayslipItemResponse> items
) {
    public static PayslipResponse from(Payslip payslip, List<PayslipItemResponse> items) {
        return new PayslipResponse(
                payslip.getId(),
                payslip.getPayrollRun().getId(),
                payslip.getEmployee().getId(),
                payslip.getEmployee().getEmployeeCode(),
                payslip.getBasicPay(),
                payslip.getTotalEarnings(),
                payslip.getTotalDeductions(),
                payslip.getEmployerContributions(),
                payslip.getNetPay(),
                payslip.getLopDays(),
                payslip.isHold(),
                payslip.getCreatedAt(),
                items
        );
    }
}
