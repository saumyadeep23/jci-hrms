package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfContributionSummaryResponse;
import in.gov.jci.hrms.dto.CpfDisputeBadgeResponse;
import in.gov.jci.hrms.dto.CpfPassbookResponseDto;
import in.gov.jci.hrms.dto.CpfPassbookSummaryResponse;
import in.gov.jci.hrms.dto.CpfPassbookTransactionDetailResponse;
import in.gov.jci.hrms.dto.CpfPassbookTransactionSummaryResponse;
import in.gov.jci.hrms.dto.CpfResolvedRateDto;
import in.gov.jci.hrms.dto.CpfTrustLedgerEntryResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTransactionDispute;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfTransactionDisputeRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backs GET /api/v1/payroll/trust/cpf/passbook/{employeeId} - the member's posted ledger plus, when this
 * FY's interest hasn't been credited yet (no ANNUAL_INTEREST/INTERIM_SETTLEMENT_INTEREST row posted),
 * an in-memory-only "shadow accrual" projection so the passbook shows an up-to-date corpus estimate
 * without writing anything to the ledger. Nothing here ever persists - the real credit only happens
 * through CpfInterestComputationService's annual run or interim-settlement crystallization.
 */
@Service
@Transactional(readOnly = true)
public class CpfTrustPassbookService {

    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfRateResolutionService rateResolutionService;
    private final CpfContributionBreakdownService contributionBreakdownService;
    private final CpfTransactionDisputeRepository disputeRepository;

    public CpfTrustPassbookService(CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfRateResolutionService rateResolutionService,
                                    CpfContributionBreakdownService contributionBreakdownService, CpfTransactionDisputeRepository disputeRepository) {
        this.ledgerRepository = ledgerRepository;
        this.rateResolutionService = rateResolutionService;
        this.contributionBreakdownService = contributionBreakdownService;
        this.disputeRepository = disputeRepository;
    }

    public CpfPassbookResponseDto getPassbook(Long employeeId, String finYear) {
        CpfResolvedRateDto resolvedRate = rateResolutionService.resolveStatutoryRate(finYear);
        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, finYear);

        BigDecimal accruedInterestFytd = accruedInterestFor(employeeId, finYear, entries, resolvedRate.baseRate());

        BigDecimal ledgerBalance = entries.isEmpty() ? BigDecimal.ZERO : entries.get(entries.size() - 1).getRunningTotalBalance();
        BigDecimal effectiveTotalCorpus = ledgerBalance.add(accruedInterestFytd);
        // Outstanding Refundable Loan Balance is read off the member's latest ledger row overall (not
        // scoped to this finYear) - runningLoanCpfBalance is maintained cross-FY, same as runningEeBalance.
        BigDecimal outstandingLoanBalance = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employeeId)
                .map(CpfTrustMemberLedgerEntry::getRunningLoanCpfBalance)
                .orElse(BigDecimal.ZERO);

        String provisionalNotice = resolvedRate.isProvisional()
                ? "Provisional rate applied as per Para 60(2) of EPF Scheme based on FY " + resolvedRate.effectiveFinYear()
                : null;

        return new CpfPassbookResponseDto(employeeId, finYear, entries.stream().map(CpfTrustLedgerEntryResponse::from).toList(),
                ledgerBalance, accruedInterestFytd, effectiveTotalCorpus, outstandingLoanBalance,
                resolvedRate.baseRate(), resolvedRate.effectiveFinYear(), resolvedRate.isProvisional(), provisionalNotice);
    }

    /**
     * Lean version of getPassbook()'s accrued-interest figure for the CPF Trust Members' List - the current
     * (still-open) FY's not-yet-posted interest only, without building the full ledger-entries response.
     * Still does the same month-end closing-balance lookups as shadowAccrual() (bounded: at most 12 per
     * call), so callers iterating a page of members should use this per-row on the current page only, never
     * against the full membership - see CpfTrustMemberDirectoryService.
     */
    public BigDecimal currentFyAccruedInterest(Long employeeId, String currentFinYear, BigDecimal baseRate) {
        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, currentFinYear);
        return accruedInterestFor(employeeId, currentFinYear, entries, baseRate);
    }

    private BigDecimal accruedInterestFor(Long employeeId, String finYear, List<CpfTrustMemberLedgerEntry> entries, BigDecimal baseRate) {
        boolean interestAlreadyPosted = entries.stream().anyMatch(e ->
                e.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST || e.getEntryType() == CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST);
        return interestAlreadyPosted ? BigDecimal.ZERO : shadowAccrual(employeeId, finYear, baseRate);
    }

    /**
     * Monthly-product interest over the elapsed April-to-today window of finYear (all 12 months if finYear
     * has already closed) - delegates to CpfInterestCalculator (mode LIVE_PROJECTION), the same engine
     * CpfInterestComputationService uses for a posted run, purely in-memory (nothing here is persisted).
     * Uses each month's OPENING balance (CpfInterestCalculator.openingBalanceDatesFor()), not its closing
     * balance - see that method's own javadoc for why a contribution posted in March never contributes to
     * this same FY's shadow accrual. Returns the calculator's whole-rupee aggregate total (never the sum of
     * un-rounded per-bucket components) since this is a single statutory-style corpus projection figure,
     * not a per-bucket ledger posting.
     */
    private BigDecimal shadowAccrual(Long employeeId, String finYear, BigDecimal baseRate) {
        LocalDate today = LocalDate.now();
        BigDecimal monthlyProductEe = BigDecimal.ZERO;
        BigDecimal monthlyProductEr = BigDecimal.ZERO;
        BigDecimal monthlyProductVpf = BigDecimal.ZERO;
        List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor(finYear);
        List<LocalDate> openingBalanceDates = CpfInterestCalculator.openingBalanceDatesFor(finYear);
        int elapsedMonths = 0;
        for (int i = 0; i < monthEnds.size(); i++) {
            if (monthEnds.get(i).isAfter(today)) {
                break;
            }
            elapsedMonths = i + 1;
            var closing = ledgerRepository.findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(employeeId, openingBalanceDates.get(i));
            if (closing.isPresent()) {
                monthlyProductEe = monthlyProductEe.add(closing.get().getRunningEeBalance());
                monthlyProductEr = monthlyProductEr.add(closing.get().getRunningErBalance());
                monthlyProductVpf = monthlyProductVpf.add(closing.get().getRunningVpfBalance());
            }
        }

        var result = CpfInterestCalculator.calculate(monthlyProductEe, monthlyProductEr, monthlyProductVpf, baseRate,
                elapsedMonths, finYear, CpfInterestCalculator.CalculationMode.LIVE_PROJECTION);
        return result.totalInterest();
    }

    // ================================================================================================
    // Self-service Passbook V2 (Parts 4/5/15/16 of the module spec) - built entirely on top of the
    // methods above (getPassbook's own summary figures, CpfContributionBreakdownService's EPS/contribution
    // figures) rather than a second passbook implementation. Callers (CpfSelfServicePassbookController)
    // always derive employeeId from the authenticated JWT, never from a client-supplied parameter - see
    // that controller's own javadoc for the IDOR protection this depends on.
    // ================================================================================================

    /** Part 4.2/4.3 summary cards + contribution summary, bundled into one lightweight response (Part 15). */
    public CpfPassbookSummaryResponse getSummary(Long employeeId, String finYear) {
        CpfPassbookResponseDto passbook = getPassbook(employeeId, finYear);
        var contributionSummary = contributionBreakdownService.summaryFor(employeeId, finYear,
                ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, finYear));
        return new CpfPassbookSummaryResponse(finYear, passbook.ledgerBalance(), passbook.accruedInterestFytd(),
                passbook.effectiveTotalCorpus(), passbook.outstandingLoanBalance(), passbook.rateApplied(),
                passbook.rateSourceFinYear(), passbook.isProvisionalRate(), passbook.provisionalNotice(),
                CpfContributionSummaryResponse.from(contributionSummary));
    }

    /**
     * Part 5/6 compact transaction list - filtered/paginated. Scoped to one financial year (matching
     * getPassbook's own convention), so this is bounded to that year's own handful-to-few-dozen rows, never
     * the member's entire historical ledger (Part 25) - filtering/paging happens in memory over that
     * already-small, already-fetched set rather than a second, more complex multi-table query, which stays
     * correct and fast at this scale without needing a bespoke join query against the dispute table.
     */
    public Page<CpfPassbookTransactionSummaryResponse> getTransactions(Long employeeId, String finYear, CpfLedgerEntryType typeFilter,
                                                                        LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, finYear);
        Map<Long, CpfTransactionDispute> latestDisputeByTxn = latestDisputeByLedgerEntry(entries);

        List<CpfPassbookTransactionSummaryResponse> filtered = entries.stream()
                .filter(e -> typeFilter == null || e.getEntryType() == typeFilter)
                .filter(e -> fromDate == null || !e.getValueDate().isBefore(fromDate))
                .filter(e -> toDate == null || !e.getValueDate().isAfter(toDate))
                .sorted(Comparator.comparing(CpfTrustMemberLedgerEntry::getValueDate).reversed()
                        .thenComparing(Comparator.comparing(CpfTrustMemberLedgerEntry::getId).reversed()))
                .map(e -> toSummary(e, latestDisputeByTxn.get(e.getId())))
                .toList();

        int start = (int) pageable.getOffset();
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), pageable, filtered.size());
        }
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    /** Part 5.1/26 full transaction detail - IDOR-checked: throws if the transaction doesn't belong to employeeId, exactly like EssPayrollService.getDetail()'s own established pattern. */
    public CpfPassbookTransactionDetailResponse getTransactionDetail(Long employeeId, Long transactionId) {
        CpfTrustMemberLedgerEntry entry = ledgerRepository.findById(transactionId)
                .orElseThrow(() -> new MasterDataNotFoundException("CPF Transaction", transactionId));
        if (!entry.getEmployee().getId().equals(employeeId)) {
            throw new BusinessRuleViolationException("CPF transaction " + transactionId + " does not belong to this employee");
        }

        BigDecimal eps = contributionBreakdownService.epsByLedgerEntry(employeeId, List.of(entry)).getOrDefault(entry.getId(), null);
        CpfDisputeBadgeResponse disputeBadge = latestDisputeByLedgerEntry(List.of(entry)).values().stream().findFirst()
                .map(CpfTrustPassbookService::toBadge).orElse(null);

        var contribution = new CpfPassbookTransactionDetailResponse.Contribution(
                entry.getEeShareCredit(), entry.getErShareCredit(), eps == null ? BigDecimal.ZERO : eps, entry.getVpfCredit(),
                entry.getEeShareCredit().add(entry.getErShareCredit()).add(eps == null ? BigDecimal.ZERO : eps).add(entry.getVpfCredit()));
        var adjustment = new CpfPassbookTransactionDetailResponse.Adjustment(
                entry.getEeShareDebit(), entry.getErShareDebit(), entry.getVpfDebit(), entry.getSancCpfLoan(),
                entry.getLoanRepayPrincipal(), entry.getLoanRepayInterest(), entry.getSancNrwEe().add(entry.getSancNrwEr()).add(entry.getSancNrwVpf()));
        var balance = new CpfPassbookTransactionDetailResponse.Balance(
                entry.getRunningEeBalance(), entry.getRunningErBalance(), entry.getRunningVpfBalance(), entry.getRunningTotalBalance());
        var audit = new CpfPassbookTransactionDetailResponse.Audit(entry.getCreatedAt(), sourceModuleFor(entry));

        return new CpfPassbookTransactionDetailResponse(entry.getId(), entry.getFinYear(), entry.getValueDate(),
                entry.getCreatedAt() != null ? entry.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate() : entry.getValueDate(),
                CpfTrustLedgerEntryResponse.from(entry).displayPeriod(), entry.getEntryType(), sourceModuleFor(entry), entry.getReferenceDocNo(),
                contribution, adjustment, balance, audit, entry.isProvisionalRate(), entry.getRateApplied(), entry.getRateSourceFinYear(), disputeBadge);
    }

    /** The most recent dispute (any status) per ledger entry, for entries that have at least one - Part 25's batched, non-N+1 lookup. */
    private Map<Long, CpfTransactionDispute> latestDisputeByLedgerEntry(List<CpfTrustMemberLedgerEntry> entries) {
        List<Long> ids = entries.stream().map(CpfTrustMemberLedgerEntry::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, CpfTransactionDispute> latest = new HashMap<>();
        for (CpfTransactionDispute dispute : disputeRepository.findByCpfLedgerTransaction_IdInOrderByRaisedAtDesc(ids)) {
            latest.putIfAbsent(dispute.getCpfLedgerTransaction().getId(), dispute); // already ordered newest-first
        }
        return latest;
    }

    private static CpfDisputeBadgeResponse toBadge(CpfTransactionDispute dispute) {
        return new CpfDisputeBadgeResponse(dispute.getId(), dispute.getDisputeNumber(), dispute.getStatus());
    }

    private CpfPassbookTransactionSummaryResponse toSummary(CpfTrustMemberLedgerEntry entry, CpfTransactionDispute dispute) {
        CpfTrustLedgerEntryResponse full = CpfTrustLedgerEntryResponse.from(entry);
        BigDecimal amount;
        boolean isCredit;
        switch (entry.getEntryType()) {
            case LOAN_WITHDRAWAL -> {
                amount = entry.getSancCpfLoan().signum() > 0 ? entry.getSancCpfLoan()
                        : entry.getSancNrwEe().add(entry.getSancNrwEr()).add(entry.getSancNrwVpf());
                isCredit = false;
            }
            case LOAN_REPAYMENT -> {
                amount = entry.getLoanRepayPrincipal().add(entry.getLoanRepayInterest());
                isCredit = true;
            }
            case ANNUAL_INTEREST_REVERSAL -> {
                amount = entry.getTotalDebit();
                isCredit = false;
            }
            default -> {
                amount = entry.getTotalCredit().signum() > 0 ? entry.getTotalCredit()
                        : entry.getEeShareCredit().add(entry.getErShareCredit()).add(entry.getVpfCredit());
                isCredit = true;
            }
        }
        return new CpfPassbookTransactionSummaryResponse(entry.getId(), entry.getValueDate(), full.displayPeriod(),
                entry.getEntryType(), amount, isCredit, dispute != null ? toBadge(dispute) : null);
    }

    private String sourceModuleFor(CpfTrustMemberLedgerEntry entry) {
        if (entry.getPayrollRun() != null || entry.getEntryType() == CpfLedgerEntryType.PAYROLL_MONTHLY || entry.getEntryType() == CpfLedgerEntryType.DA_ARREAR) {
            return "Payroll";
        }
        if (entry.getTransfer() != null || entry.getEntryType() == CpfLedgerEntryType.TRANSFER_IN) {
            return "Incoming Fund Transfer";
        }
        if (entry.getLoan() != null || entry.getEntryType() == CpfLedgerEntryType.LOAN_WITHDRAWAL || entry.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT) {
            return "CPF Trust Loan";
        }
        if (entry.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST || entry.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST_REVERSAL
                || entry.getEntryType() == CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST) {
            return "CPF Interest";
        }
        if (entry.getEntryType() == CpfLedgerEntryType.FINAL_SETTLEMENT) {
            return "Terminal Settlement";
        }
        return "CPF Trust";
    }
}
