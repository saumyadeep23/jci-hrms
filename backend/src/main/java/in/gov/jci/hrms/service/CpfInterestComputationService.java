package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Ledger-posting primitives shared by every CPF interest workflow. All actual interest arithmetic (the
 * monthly-product formula and its component-precision/aggregate-whole-rupee rounding rule) is delegated to
 * {@link CpfInterestCalculator} - see that class's own javadoc for the full statutory formula and rounding
 * rationale. This class owns:
 * <ul>
 *   <li>{@link #crystallizeInterimInterest} - mid-year exit/settlement interest, posted directly (a
 *       settlement is a one-shot terminal action with no preview/approve step of its own).</li>
 *   <li>{@link #postInterestEntry} and {@link #cascadeRunningBalanceOffset} - the shared "insert one interest
 *       ledger row, then push its effect through every later row this member already has" mechanics that
 *       {@link CpfInterestRunService} builds its Calculate/Post/Reverse workflow on top of for the year-end
 *       ANNUAL_INTEREST run (see that class's own javadoc for why year-end interest needs a full
 *       admin-controlled workflow that a settlement's one-shot interim crystallization does not).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CpfInterestComputationService {

    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final EmployeeRepository employeeRepository;
    private final CpfRateResolutionService rateResolutionService;

    public CpfInterestComputationService(CpfTrustMemberLedgerEntryRepository ledgerRepository,
                                          EmployeeRepository employeeRepository, CpfRateResolutionService rateResolutionService) {
        this.ledgerRepository = ledgerRepository;
        this.employeeRepository = employeeRepository;
        this.rateResolutionService = rateResolutionService;
    }

    /**
     * Mid-year interest crystallization on exit (superannuation/resignation/death/transfer-out), so a
     * member's final settlement doesn't have to wait for the normal year-end ANNUAL_INTEREST run. Resolves
     * the rate via CpfRateResolutionService (applying the Para 60(2) preceding-FY fallback when this FY's
     * rate isn't notified yet) and applies the same CpfInterestCalculator formula as the year-end run, but
     * only over the elapsed April-to-settlement-month window (cutoffMonth = however many FY months have
     * closed as of settlementDate - see CpfInterestCalculator.fyMonthIndexFor()).
     *
     * <p>settlementDate here is whatever the caller (the exit/terminal-settlement workflow) determines is
     * the actual interest cutoff date under JCI's adopted Para 60 settlement provisions - this method does
     * not itself decide "cutoff = exit month" or apply any 25th-of-month rule; no such rule exists anywhere
     * else in this codebase today (confirmed by inspection), so none is invented here. If JCI's settlement
     * workflow needs a specific authorization-date-driven cutoff (e.g. interest only up to the month
     * preceding claim authorization), the caller should resolve that date and pass it as settlementDate.
     */
    @Transactional
    public CpfTrustMemberLedgerEntry crystallizeInterimInterest(Long employeeId, LocalDate settlementDate, String settlementType, Long officerId) {
        String finYear = CpfInterestCalculator.finYearFor(settlementDate);
        List<CpfTrustMemberLedgerEntry> existing = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employeeId, finYear);
        boolean alreadyPosted = existing.stream().anyMatch(e ->
                e.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST || e.getEntryType() == CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST);
        if (alreadyPosted) {
            throw new BusinessRuleViolationException(
                    "Interest has already been posted for employee " + employeeId + " in FY " + finYear + " - cannot crystallize interim interest again");
        }

        var resolvedRate = rateResolutionService.resolveStatutoryRate(finYear);

        BigDecimal monthlyProductEe = BigDecimal.ZERO;
        BigDecimal monthlyProductEr = BigDecimal.ZERO;
        BigDecimal monthlyProductVpf = BigDecimal.ZERO;
        List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor(finYear);
        List<LocalDate> openingBalanceDates = CpfInterestCalculator.openingBalanceDatesFor(finYear);
        int elapsedMonths = 0;
        for (int i = 0; i < monthEnds.size(); i++) {
            if (monthEnds.get(i).isAfter(settlementDate)) {
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

        var result = CpfInterestCalculator.calculate(monthlyProductEe, monthlyProductEr, monthlyProductVpf, resolvedRate.baseRate(),
                elapsedMonths, finYear, CpfInterestCalculator.CalculationMode.MID_YEAR_SETTLEMENT);

        Employee employee = employeeRepository.getReferenceById(employeeId);
        String remarks = "Interim interest (" + (resolvedRate.isProvisional() ? "Provisional Para 60(2)" : "Final") + ") for "
                + settlementType + " up to " + settlementDate
                + (officerId != null ? " (crystallized by officer " + officerId + ")" : "");
        CpfTrustMemberLedgerEntry entry = postInterestEntry(employee, finYear, settlementDate, CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST,
                result, resolvedRate.isProvisional(), resolvedRate.baseRate(), resolvedRate.effectiveFinYear(), remarks);
        // A settlement's interim interest is always dated at/after the member's own latest existing row
        // (it fires at exit, when no further contributions follow), so no cascade is needed here - unlike
        // CpfInterestRunService's year-end postings, which insert into the middle of an already-migrated
        // multi-year ledger. See cascadeRunningBalanceOffset's own javadoc.
        return entry;
    }

    /**
     * Appends one interest entry on top of whatever running balance the member's LATEST existing entry (any
     * type, any date) left behind - i.e. this always inserts at the logical end of the member's ledger.
     * Callers that need to insert at an earlier chronological point (a historical year-end run posted after
     * later years' data already exists) must call {@link #cascadeRunningBalanceOffset} afterwards to push
     * this entry's effect through every later row - see that method's own javadoc.
     *
     * Per-bucket ee/er/vpf credits are stored at 2-decimal-place currency precision (the ledger columns are
     * NUMERIC(12,2) and this is the calculator's own full-precision result, not further truncated to whole
     * rupees); interestCredit/totalCredit store the calculator's already-whole-rupee aggregate. Because of
     * this split, runningTotalBalance (= runningEe+runningEr+runningVpf, this entity's own established
     * convention) can differ from "prior total + whole-rupee total interest" by a few paise - a deliberate,
     * documented consequence of never rounding EE/ER/VPF individually before aggregation (see
     * CpfInterestCalculator's own javadoc).
     */
    CpfTrustMemberLedgerEntry postInterestEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                                 CpfInterestCalculator.CpfInterestCalculationResult result,
                                                 boolean isProvisionalRate, BigDecimal rateApplied, String rateSourceFinYear, String remarks) {
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }
        return buildAndSaveInterestEntry(employee, finYear, valueDate, entryType, result, isProvisionalRate, rateApplied, rateSourceFinYear,
                remarks, priorEe, priorEr, priorVpf);
    }

    /**
     * Like {@link #postInterestEntry}, but the caller supplies the prior EE/ER/VPF balance explicitly
     * (rather than this method assuming the member's absolute-latest row is the correct base) - required
     * whenever the insertion point is NOT the member's ledger's logical end, i.e. every year-end run
     * {@link CpfInterestRunService} posts against the already-fully-migrated 2010-2026 ledger. Callers must
     * follow this with {@link #cascadeRunningBalanceOffset} to push the credit through later rows.
     */
    CpfTrustMemberLedgerEntry postHistoricalInterestEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                                           CpfInterestCalculator.CpfInterestCalculationResult result,
                                                           BigDecimal priorEe, BigDecimal priorEr, BigDecimal priorVpf,
                                                           BigDecimal rateApplied, CpfAnnualInterestRun interestRun, String remarks) {
        CpfTrustMemberLedgerEntry entry = buildAndSaveInterestEntry(employee, finYear, valueDate, entryType, result, false, rateApplied,
                finYear, remarks, priorEe, priorEr, priorVpf);
        entry.setInterestRun(interestRun);
        return entry;
    }

    /**
     * Reverses one previously-posted ANNUAL_INTEREST row without deleting it (Part 23 of the module spec):
     * inserts an ANNUAL_INTEREST_REVERSAL row at {@code valueDate} (the reversal action's own date, always
     * later than the original posting) carrying DEBIT amounts equal to what was originally credited -
     * mirroring how LOAN_WITHDRAWAL already reduces a running balance via debit fields, rather than
     * inventing a "negative credit" convention. priorEe/priorEr/priorVpf must be this member's running
     * balance immediately before {@code valueDate} (from {@link #closingBalanceAsOf}); the caller must
     * follow this with {@link #cascadeRunningBalanceOffset} using the NEGATED amounts to push the reversal
     * through any rows already dated after {@code valueDate}.
     */
    CpfTrustMemberLedgerEntry postReversalEntry(Employee employee, String finYear, LocalDate valueDate,
                                                 BigDecimal eeAmount, BigDecimal erAmount, BigDecimal vpfAmount,
                                                 BigDecimal priorEe, BigDecimal priorEr, BigDecimal priorVpf,
                                                 CpfAnnualInterestRun interestRun, String remarks) {
        BigDecimal newEe = priorEe.subtract(eeAmount);
        BigDecimal newEr = priorEr.subtract(erAmount);
        BigDecimal newVpf = priorVpf.subtract(vpfAmount);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate,
                CpfLedgerEntryType.ANNUAL_INTEREST_REVERSAL, newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setEeShareDebit(eeAmount);
        entry.setErShareDebit(erAmount);
        entry.setVpfDebit(vpfAmount);
        entry.setTotalDebit(eeAmount.add(erAmount).add(vpfAmount));
        entry.setRemarks(remarks);
        entry.setInterestRun(interestRun);
        return ledgerRepository.save(entry);
    }

    private CpfTrustMemberLedgerEntry buildAndSaveInterestEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                                                  CpfInterestCalculator.CpfInterestCalculationResult result,
                                                                  boolean isProvisionalRate, BigDecimal rateApplied, String rateSourceFinYear,
                                                                  String remarks, BigDecimal priorEe, BigDecimal priorEr, BigDecimal priorVpf) {
        BigDecimal eeCredit = result.eeInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal erCredit = result.erInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal vpfCredit = result.vpfInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal newEe = priorEe.add(eeCredit);
        BigDecimal newEr = priorEr.add(erCredit);
        BigDecimal newVpf = priorVpf.add(vpfCredit);

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate,
                entryType, newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setInterestCredit(result.totalInterest());
        entry.setEeShareCredit(eeCredit);
        entry.setErShareCredit(erCredit);
        entry.setVpfCredit(vpfCredit);
        entry.setTotalCredit(result.totalInterest());
        entry.setProvisionalRate(isProvisionalRate);
        entry.setRateApplied(rateApplied);
        entry.setRateSourceFinYear(rateSourceFinYear);
        entry.setRemarks(remarks);
        return ledgerRepository.save(entry);
    }

    /**
     * Inserts a historical interest entry AT the correct chronological position (rather than appending it
     * at the member's ledger's logical end, like {@link #postInterestEntry} does) and pushes its effect
     * through every row the member already has after it - Part 18 of the CPF Interest Management module
     * spec ("running-balance cascade"). Needed because the migrated ledger already spans 2010-2026 in full:
     * posting FY2011-12's interest today inserts a March-2012-dated row into the MIDDLE of a member's
     * already-existing 2012-2026 history, not at its end, so every one of that member's rows after
     * March 2012 is carrying a running balance computed without this credit and must be corrected.
     *
     * <p>Because a member's EE/ER/VPF running balances are simple running sums, correcting them for an
     * inserted credit (or, for a reversal, an inserted debit) is a constant OFFSET applied to every
     * subsequent row - not a full replay from scratch - which is exactly what
     * {@link CpfTrustMemberLedgerEntry#applyRunningBalanceCascadeOffset} does, preserving every row's own
     * credit/debit amount columns, its loan/NRW running balances (interest never touches those), and its
     * EE+ER+VPF=Total invariant. deltaEe/deltaEr/deltaVpf are signed: positive for a posting, negative
     * (i.e. the negated credited amounts) for {@code CpfInterestRunService.reverseRun()}'s reversal.
     */
    void cascadeRunningBalanceOffset(Long employeeId, LocalDate insertedAt, BigDecimal deltaEe, BigDecimal deltaEr, BigDecimal deltaVpf) {
        for (CpfTrustMemberLedgerEntry subsequent : ledgerRepository
                .findByEmployee_IdAndValueDateGreaterThanOrderByValueDateAscIdAsc(employeeId, insertedAt)) {
            subsequent.applyRunningBalanceCascadeOffset(deltaEe, deltaEr, deltaVpf);
        }
    }

    /**
     * The member's running EE/ER/VPF balance as of (on or before) a given date - CpfInterestRunService uses
     * this both for the monthly-product accumulation and, at the same date, as the base
     * {@link #postInterestEntry} would have used had the insertion point been the member's latest row (see
     * that method's own "always inserts at the end" caveat).
     */
    Optional<CpfTrustMemberLedgerEntry> closingBalanceAsOf(Long employeeId, LocalDate onOrBefore) {
        return ledgerRepository.findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(employeeId, onOrBefore);
    }
}
