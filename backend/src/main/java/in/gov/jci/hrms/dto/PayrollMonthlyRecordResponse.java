package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/payroll/batches/{batchId}/records staging-sheet row - earnings/deductions/gross/net plus Head 20 and TDS-override call-outs. */
public record PayrollMonthlyRecordResponse(
        Long tranId,
        String empCode,
        String employeeName,
        int month,
        int year,
        BigDecimal basicPay,
        BigDecimal grossAmount,
        BigDecimal totalDeductions,
        BigDecimal netAmount,
        boolean salaryHeld,
        BigDecimal leaveEncashmentAmount,
        BigDecimal tdsAmount,
        boolean tdsOverridden,
        List<PayrollMonthlyHeadItemResponse> headItems,
        List<PayrollHeadLineResponse> fullHeadLines) {
}
