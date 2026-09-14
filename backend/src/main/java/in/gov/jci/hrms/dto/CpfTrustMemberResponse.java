package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfSettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the CPF Trust Members' List - GET /api/v1/payroll/trust/members. cpfAcNo/uanNo are the primary
 * identifiers this list is keyed by (see the controller/service javadoc), NOT employeeCode/departmentName -
 * those are kept only as light supporting context for the search/UI, never as the primary column.
 *
 * cpfBalance/totalPayable are always eeBalance+vpfBalance+erBalance (and +accruedInterest for the latter) -
 * computed here from the underlying ledger balances, never independently entered - see
 * CpfTrustMemberDirectoryService.toResponse().
 */
public record CpfTrustMemberResponse(
        Long employeeId,
        String employeeCode,
        String fullName,
        String cpfAcNo,
        String uanNo,
        String status,
        boolean isSeparated,
        LocalDate separationDate,

        BigDecimal eeBalance,
        BigDecimal vpfBalance,
        BigDecimal erBalance,
        /** eeBalance + vpfBalance + erBalance. */
        BigDecimal cpfBalance,
        /** Current-FY interest not yet posted by an annual interest run (CpfTrustPassbookService's shadow-accrual projection) - 0 once posted, since posted interest is already folded into the balances above. */
        BigDecimal accruedInterest,
        /** cpfBalance + accruedInterest. */
        BigDecimal totalPayable,

        /** Raw TerminalSettlementStatus (DRAFT/AUDITED/APPROVED/DISBURSED), or null if no terminal settlement exists yet. Kept for callers that need the underlying workflow state; UI should prefer settlementStatus. */
        String rawTerminalSettlementStatus,
        CpfSettlementStatus settlementStatus,
        LocalDate settlementDueDate,
        LocalDate settlementDate,
        String settlementLagLabel,
        Long settlementLagDays
) {
}
