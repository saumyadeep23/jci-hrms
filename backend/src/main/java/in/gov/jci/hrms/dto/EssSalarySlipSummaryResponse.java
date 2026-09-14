package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** One row of GET /api/v1/ess/salary-slips - only DISBURSED batches are visible, see EssPayrollService. */
public record EssSalarySlipSummaryResponse(Long tranId, int month, int year, String batchNo, BigDecimal grossAmount,
                                            BigDecimal totalDeductions, BigDecimal netAmount, boolean salaryHeld) {
}
