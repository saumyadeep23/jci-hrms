package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;

import java.math.BigDecimal;

/** One non-zero salary-head line item on a payroll_monthly_records staging-sheet row. */
public record PayrollMonthlyHeadItemResponse(int headCount, BigDecimal amount) {

    public static PayrollMonthlyHeadItemResponse from(PayrollMonthlyHeadItem item) {
        return new PayrollMonthlyHeadItemResponse(item.getHeadCount(), item.getAmount());
    }
}
