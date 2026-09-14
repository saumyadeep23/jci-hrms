package in.gov.jci.hrms.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * POST /api/v1/payroll/trust/withdrawal-rules/simulate - Part 24's "Rule Simulator": lets a Trust official
 * test a proposed rule (including a DRAFT/PENDING one not yet approved, via ruleVersionId) before it goes
 * live, or sanity-check the currently-active rule for a real/hypothetical member. employeeCode is optional -
 * when supplied, service-eligibility/frequency/concurrency are evaluated against that real employee's
 * actual data; when omitted, those checks are skipped (assumed eligible) and only the ceiling/head
 * allocation math runs against the manually-entered figures below.
 */
public record CpfRuleSimulatorRequest(
        @NotBlank String purposeCode,
        /** When null, simulates against the currently-active APPROVED version for this purpose. */
        String ruleVersionId,
        String employeeCode,
        BigDecimal headABalance,
        BigDecimal headBBalance,
        BigDecimal headCBalance,
        BigDecimal basicPlusDa,
        BigDecimal propertyCost,
        BigDecimal existingPayrollDeductions,
        BigDecimal requestedAmount,
        Integer loanTenureMonths
) {
}
