package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayslipSummaryResponse(
        Long payrollRunId,
        Integer cycleYear,
        Integer cycleMonth,
        LocalDate startDate,
        LocalDate endDate,
        Long employeeId,
        String employeeCode,
        String employeeName,
        BigDecimal basicPay,
        BigDecimal totalEarnings,
        BigDecimal totalDeductions,
        BigDecimal employerContributions,
        BigDecimal netPay,
        /**
         * Always ZERO today - this system has no income-tax (TDS) computation
         * module anywhere; the only deduction head seeded is EPF_EE. Computed
         * defensively as (total deduction line items - employee EPF) rather
         * than hardcoded, so a future non-PF deduction head picks this up
         * automatically without a payslip-summary code change.
         */
        BigDecimal taxDeductions,
        AttendanceBreakdownResponse attendance,
        PfBucketSplitResponse pfBucketSplit,
        List<PayslipItemResponse> lineItems
) {
}
