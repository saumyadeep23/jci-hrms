package in.gov.jci.hrms.dto;

import java.math.BigDecimal;

/** One row of the zero-filled reconciliation matrix - see PayrollQueryService.fullHeadLines(). category is "EARNING"/"DEDUCTION" (from SalaryHead.effectType) or "STATUTORY" (employer-side, from StatutoryHead - never appears on the employee's own payslip). */
public record PayrollHeadLineResponse(int headCount, String shortName, String description, String category, BigDecimal amount) {
}
