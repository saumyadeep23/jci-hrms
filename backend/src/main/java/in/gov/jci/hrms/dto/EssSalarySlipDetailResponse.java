package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET .../ess/salary-slips/{tranId} detail (also what the printable modal renders) - the full zero-filled head matrix for one month. */
public record EssSalarySlipDetailResponse(
        Long tranId, String empCode, String employeeName, String designation, int month, int year,
        BigDecimal grossAmount, BigDecimal totalDeductions, BigDecimal netAmount,
        List<PayrollHeadLineResponse> headLines) {
}
