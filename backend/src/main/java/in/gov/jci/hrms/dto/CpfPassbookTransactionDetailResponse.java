package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * GET /api/v1/ess/cpf/passbook/transactions/{id} - the read-only, fully authoritative expanded-detail view
 * (Part 5.1/26). Every figure is read directly off the immutable {@code CpfTrustMemberLedgerEntry} row (or,
 * for EPS, {@code CpfContributionBreakdownService}) - nothing here is recomputed/reconstructed in the
 * frontend. Sections mirror Part 5.1's own grouping so the UI can hide a whole section when every field in
 * it is not applicable to this entry's type (e.g. a PAYROLL_MONTHLY row has no loan figures at all) rather
 * than rendering a wall of zeros.
 */
public record CpfPassbookTransactionDetailResponse(
        Long id,
        String finYear,
        LocalDate transactionDate,
        LocalDate postingDate,
        String displayPeriod,
        CpfLedgerEntryType transactionType,
        String source,
        String referenceDocNo,
        Contribution contribution,
        Adjustment adjustment,
        Balance balance,
        Audit audit,
        boolean isProvisionalRate,
        BigDecimal rateApplied,
        String rateSourceFinYear,
        CpfDisputeBadgeResponse dispute
) {
    public record Contribution(BigDecimal employeeCpf, BigDecimal employerCpf, BigDecimal eps, BigDecimal vpf, BigDecimal total) {
    }

    public record Adjustment(BigDecimal employeeDebit, BigDecimal employerDebit, BigDecimal vpfDebit,
                              BigDecimal loanSanctioned, BigDecimal loanPrincipalRepaid, BigDecimal loanInterestRepaid,
                              BigDecimal nonRefundableWithdrawal) {
    }

    public record Balance(BigDecimal employeeBalance, BigDecimal employerBalance, BigDecimal vpfBalance, BigDecimal totalBalance) {
    }

    public record Audit(Instant createdAt, String sourceModule) {
    }
}
