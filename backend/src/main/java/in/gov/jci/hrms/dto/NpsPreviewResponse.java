package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/payroll/declarations/nps/employee/{employeeId}/preview - basicPay/dearnessAllowance
 * are the employee's current snapshot values; the frontend computes the live "Estimated Monthly
 * Deduction" badge reactively as the percentage input changes, round((basicPay + dearnessAllowance)
 * * pct / 100) - not recomputed server-side per keystroke.
 */
public record NpsPreviewResponse(
        Long employeeId,
        String employeeCode,
        String employeeName,
        BigDecimal basicPay,
        BigDecimal dearnessAllowance,
        String currentFinancialYear,
        boolean alreadyDeclaredForCurrentFy,
        NpsDeclarationResponse currentFyDeclaration,
        List<NpsDeclarationResponse> history
) {
}
