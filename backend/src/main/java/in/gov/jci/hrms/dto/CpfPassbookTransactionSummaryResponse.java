package in.gov.jci.hrms.dto;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One compact row of the Passbook V2 transaction list (Part 5) - deliberately NOT every ledger column
 * (that full breakdown is CpfPassbookTransactionDetailResponse, fetched only when a row is expanded).
 * displayAmount is the single most relevant figure for this row's type (net credit for a payroll/interest
 * posting, debit for a withdrawal/loan sanction, principal+interest for a repayment) - computed server-side
 * so the UI never has to infer "which column matters" itself.
 */
public record CpfPassbookTransactionSummaryResponse(
        Long id,
        LocalDate transactionDate,
        String displayPeriod,
        CpfLedgerEntryType transactionType,
        BigDecimal displayAmount,
        boolean isCredit,
        CpfDisputeBadgeResponse dispute
) {
}
