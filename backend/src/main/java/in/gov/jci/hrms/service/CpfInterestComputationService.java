package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfAnnualInterestRunRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Statutory year-end interest declaration for the CPF Trust - one run credits every member with ledger
 * activity, per bucket (EE/ER/VPF), using the standard "monthly product" method: for each of the 12
 * April-March months, take the member's closing balance at that month-end, sum those 12 figures, then
 * apply (monthlyProduct * declaredRate) / 1200.
 *
 * Point-in-time rule for a TRANSFER_IN member (joined mid-year via an incoming transfer): this needs no
 * special-casing - CpfTrustMemberLedgerEntryRepository's "closing balance as of month-end" lookup
 * naturally returns empty (treated as zero) for any month before the member's first-ever ledger row (the
 * TRANSFER_IN entry itself, dated at bank_realization_date), so months before that already correctly
 * contribute zero to the monthly product.
 *
 * cpf_annual_interest_runs.fin_year is UNIQUE (the live schema's own constraint - see V74's header
 * comment), so at most one run row exists per FY; re-running an already-POSTED FY is rejected rather than
 * silently overwritten, since correcting a posted declaration is a deliberate SUPERSEDED-then-repost
 * decision this method does not make on its own.
 */
@Service
@Transactional(readOnly = true)
public class CpfInterestComputationService {

    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfAnnualInterestRunRepository interestRunRepository;
    private final EmployeeRepository employeeRepository;
    private final CpfRateResolutionService rateResolutionService;

    public CpfInterestComputationService(CpfTrustMemberLedgerEntryRepository ledgerRepository,
                                          CpfAnnualInterestRunRepository interestRunRepository, EmployeeRepository employeeRepository,
                                          CpfRateResolutionService rateResolutionService) {
        this.ledgerRepository = ledgerRepository;
        this.interestRunRepository = interestRunRepository;
        this.employeeRepository = employeeRepository;
        this.rateResolutionService = rateResolutionService;
    }

    @Transactional
    public CpfAnnualInterestRunResponse computeAnnualInterest(String finYear, BigDecimal declaredRate, String interestOrderNo,
                                                                LocalDate interestOrderDate, Long postedByOfficerId) {
        if (!interestRunRepository.findByFinYear(finYear).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "An interest run already exists for FY " + finYear + " - supersede it before re-running");
        }

        List<LocalDate> monthEnds = monthEndsFor(finYear);
        LocalDate marchThirtyFirst = monthEnds.get(monthEnds.size() - 1);

        CpfAnnualInterestRun run = new CpfAnnualInterestRun(finYear, declaredRate, interestOrderNo, interestOrderDate);
        run.setStatus(CpfInterestRunStatus.DRAFT);

        int membersProcessed = 0;
        BigDecimal totalEe = BigDecimal.ZERO;
        BigDecimal totalEr = BigDecimal.ZERO;
        BigDecimal totalVpf = BigDecimal.ZERO;

        for (Long employeeId : ledgerRepository.findDistinctEmployeeIds()) {
            BigDecimal monthlyProductEe = BigDecimal.ZERO;
            BigDecimal monthlyProductEr = BigDecimal.ZERO;
            BigDecimal monthlyProductVpf = BigDecimal.ZERO;
            for (LocalDate monthEnd : monthEnds) {
                Optional<CpfTrustMemberLedgerEntry> closing = ledgerRepository
                        .findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(employeeId, monthEnd);
                if (closing.isPresent()) {
                    monthlyProductEe = monthlyProductEe.add(closing.get().getRunningEeBalance());
                    monthlyProductEr = monthlyProductEr.add(closing.get().getRunningErBalance());
                    monthlyProductVpf = monthlyProductVpf.add(closing.get().getRunningVpfBalance());
                }
            }

            BigDecimal interestEe = interestFor(monthlyProductEe, declaredRate);
            BigDecimal interestEr = interestFor(monthlyProductEr, declaredRate);
            BigDecimal interestVpf = interestFor(monthlyProductVpf, declaredRate);
            BigDecimal totalInterest = interestEe.add(interestEr).add(interestVpf);
            if (totalInterest.signum() <= 0) {
                continue;
            }

            Employee employee = employeeRepository.getReferenceById(employeeId);
            // An annual run is only ever invoked with a rate the caller already knows is final for this FY
            // (declaredRate is supplied directly, not resolved via CpfRateResolutionService), so these
            // entries are never provisional - unlike crystallizeInterimInterest()'s Para 60(2) entries below.
            postInterestEntry(employee, finYear, marchThirtyFirst, CpfLedgerEntryType.ANNUAL_INTEREST, interestEe, interestEr, interestVpf,
                    false, declaredRate, finYear, "Annual interest for FY " + finYear);

            membersProcessed++;
            totalEe = totalEe.add(interestEe);
            totalEr = totalEr.add(interestEr);
            totalVpf = totalVpf.add(interestVpf);
        }

        run.setTotalMembersProcessed(membersProcessed);
        run.setTotalInterestCreditedEe(totalEe);
        run.setTotalInterestCreditedEr(totalEr);
        run.setTotalInterestCreditedVpf(totalVpf);
        run.setStatus(CpfInterestRunStatus.POSTED);
        run.setPostedAt(Instant.now());
        if (postedByOfficerId != null) {
            employeeRepository.findById(postedByOfficerId).ifPresent(run::setPostedBy);
        }

        return CpfAnnualInterestRunResponse.from(interestRunRepository.saveAndFlush(run));
    }

    /** Shared by computeAnnualInterest() and crystallizeInterimInterest() - appends one interest entry on top of whatever running balance the member's prior entry (any type) left behind. */
    private CpfTrustMemberLedgerEntry postInterestEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                                          BigDecimal interestEe, BigDecimal interestEr, BigDecimal interestVpf,
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
        BigDecimal newEe = priorEe.add(interestEe);
        BigDecimal newEr = priorEr.add(interestEr);
        BigDecimal newVpf = priorVpf.add(interestVpf);
        BigDecimal totalInterest = interestEe.add(interestEr).add(interestVpf);

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate,
                entryType, newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setInterestCredit(totalInterest);
        entry.setEeShareCredit(interestEe);
        entry.setErShareCredit(interestEr);
        entry.setVpfCredit(interestVpf);
        entry.setTotalCredit(totalInterest);
        entry.setProvisionalRate(isProvisionalRate);
        entry.setRateApplied(rateApplied);
        entry.setRateSourceFinYear(rateSourceFinYear);
        entry.setRemarks(remarks);
        return ledgerRepository.save(entry);
    }

    /** Package-visible so CpfTrustPassbookService's shadow accrual reuses the exact same rounding as a posted run. */
    static BigDecimal interestFor(BigDecimal monthlyProduct, BigDecimal declaredRate) {
        if (monthlyProduct.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return monthlyProduct.multiply(declaredRate).divide(TWELVE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * Mid-year interest crystallization on exit (superannuation/resignation/death/transfer-out), so a
     * member's final settlement doesn't have to wait for the normal year-end ANNUAL_INTEREST run. Resolves
     * the rate via CpfRateResolutionService (applying the Para 60(2) preceding-FY fallback when this FY's
     * rate isn't notified yet) and applies the same monthly-product formula as computeAnnualInterest(), but
     * only over the elapsed April-to-settlement-month window.
     */
    @Transactional
    public CpfTrustMemberLedgerEntry crystallizeInterimInterest(Long employeeId, LocalDate settlementDate, String settlementType, Long officerId) {
        String finYear = finYearFor(settlementDate);
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
        for (LocalDate monthEnd : monthEndsFor(finYear)) {
            if (monthEnd.isAfter(settlementDate)) {
                break;
            }
            var closing = ledgerRepository.findFirstByEmployee_IdAndValueDateLessThanEqualOrderByValueDateDescIdDesc(employeeId, monthEnd);
            if (closing.isPresent()) {
                monthlyProductEe = monthlyProductEe.add(closing.get().getRunningEeBalance());
                monthlyProductEr = monthlyProductEr.add(closing.get().getRunningErBalance());
                monthlyProductVpf = monthlyProductVpf.add(closing.get().getRunningVpfBalance());
            }
        }

        BigDecimal interestEe = interestFor(monthlyProductEe, resolvedRate.baseRate());
        BigDecimal interestEr = interestFor(monthlyProductEr, resolvedRate.baseRate());
        BigDecimal interestVpf = interestFor(monthlyProductVpf, resolvedRate.baseRate());

        Employee employee = employeeRepository.getReferenceById(employeeId);
        String remarks = "Interim interest (" + (resolvedRate.isProvisional() ? "Provisional Para 60(2)" : "Final") + ") for "
                + settlementType + " up to " + settlementDate
                + (officerId != null ? " (crystallized by officer " + officerId + ")" : "");
        return postInterestEntry(employee, finYear, settlementDate, CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST,
                interestEe, interestEr, interestVpf, resolvedRate.isProvisional(), resolvedRate.baseRate(), resolvedRate.effectiveFinYear(), remarks);
    }

    /** The "YYYY-YYYY" FY (April-March) that a given date falls in. */
    static String finYearFor(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + (startYear + 1);
    }

    /** The 12 April-March month-end dates for a "YYYY-YYYY" finYear label. */
    static List<LocalDate> monthEndsFor(String finYear) {
        String[] parts = finYear.split("-");
        if (parts.length != 2) {
            throw new BusinessRuleViolationException("finYear must be in \"YYYY-YYYY\" form, got: " + finYear);
        }
        int startYear = Integer.parseInt(parts[0]);
        List<LocalDate> monthEnds = new ArrayList<>(12);
        YearMonth cursor = YearMonth.of(startYear, 4);
        for (int i = 0; i < 12; i++) {
            monthEnds.add(cursor.atEndOfMonth());
            cursor = cursor.plusMonths(1);
        }
        return monthEnds;
    }
}
