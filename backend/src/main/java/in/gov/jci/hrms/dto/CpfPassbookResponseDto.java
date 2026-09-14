package in.gov.jci.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** GET /api/v1/payroll/trust/cpf/passbook/{employeeId}?finYear= - CpfTrustPassbookService.getPassbook(). */
public record CpfPassbookResponseDto(
        Long employeeId,
        String finYear,
        List<CpfTrustLedgerEntryResponse> entries,
        BigDecimal ledgerBalance,
        BigDecimal accruedInterestFytd,
        BigDecimal effectiveTotalCorpus,
        /** Current Outstanding Refundable Loan Balance (running_loan_cpf_balance on the member's latest ledger row, across all financial years) - 0 if no CPF Trust loan has ever been disbursed. */
        BigDecimal outstandingLoanBalance,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        boolean isProvisionalRate,
        String provisionalNotice
) {
}
