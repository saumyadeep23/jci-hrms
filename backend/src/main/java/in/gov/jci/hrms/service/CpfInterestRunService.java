package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.dto.CpfInterestCalculateRequest;
import in.gov.jci.hrms.dto.CpfInterestCalculationPreviewResponse;
import in.gov.jci.hrms.dto.CpfInterestFinancialYearStatusResponse;
import in.gov.jci.hrms.dto.CpfInterestMemberBreakdown;
import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunScope;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfAnnualInterestRunRepository;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The CPF Interest Management module's Calculate -> Approve/Post -> Reverse -> Recalculate workflow
 * (replacing the old one-shot {@code CpfInterestComputationService.computeAnnualInterest}, which posted
 * directly with no preview/approval step and no way to reverse a mistake). All actual interest arithmetic
 * still comes from {@link CpfInterestCalculator} - this class's own job is: decide which financial years
 * and members are eligible, orchestrate the calculate/post/reverse lifecycle against
 * {@code cpf_annual_interest_runs}, and delegate the actual ledger writes and running-balance cascade to
 * {@link CpfInterestComputationService}.
 *
 * <h2>Why a workflow, not a single "compute and post" call</h2>
 * The migrated ledger already spans April-2010 through August-2026 in full for every member, with no
 * financial year's ANNUAL_INTEREST ever posted. Positing all of it in one shot would mean: no chance to
 * review each year's figures before they become part of members' statutory corpus, no way to correct a
 * wrong rate/order-no after the fact without deleting accounting history, and no visibility into which
 * years are even safe to calculate at all (an opening-balance basis has to exist - see
 * {@link #firstEligibleFinYear}). Hence: CALCULATED (a persisted, reviewable preview; no ledger write) -&gt;
 * POSTED (ledger entries inserted, {@link CpfInterestComputationService#cascadeRunningBalanceOffset}
 * pushes the effect through every later row the member already has) -&gt; REVERSED (an
 * ANNUAL_INTEREST_REVERSAL entry per member, original entries untouched) -&gt; a fresh CALCULATED run for
 * the same FY once corrected.
 *
 * <h2>Financial-year eligibility is derived from the ledger, never hardcoded</h2>
 * {@link #firstEligibleFinYear()} and {@link #lastEligibleFinYear()} read the ledger's own earliest/latest
 * {@code value_date} rather than assuming "FY2011-12 is the first year" or "FY2025-26 is the last complete
 * year" - both of those happen to be true on the current dataset, but this class does not encode them as
 * constants (inspecting the live dev DB while building this class found the ledger's actual earliest row
 * dated 2010-03-31 - an OPENING_BALANCE row for two legacy members - making FY2010-2011 the first
 * calculable year today, one year earlier than a hardcoded "2011-12" assumption would have allowed).
 *
 * <h2>Sequential posting</h2>
 * For an ALL_MEMBERS-scope run, {@link #sequentialBlockReason} requires the immediately preceding FY to
 * already be POSTED before this FY can be, UNLESS this FY is the first eligible one - preventing an
 * out-of-order historical posting from crediting interest on top of an opening corpus that itself doesn't
 * yet include the preceding year's interest. A SELECTED_MEMBER correction run is exempt (Part 26 of the
 * module spec: member-level correction must be possible without re-litigating every other member's FY).
 */
@Service
public class CpfInterestRunService {

    private static final List<CpfInterestRunStatus> INACTIVE_STATUSES = List.of(CpfInterestRunStatus.REVERSED, CpfInterestRunStatus.FAILED);

    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfAnnualInterestRunRepository runRepository;
    private final CpfStatutoryInterestRateRepository rateRepository;
    private final EmployeeRepository employeeRepository;
    private final CpfInterestComputationService interestComputationService;

    public CpfInterestRunService(CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfAnnualInterestRunRepository runRepository,
                                  CpfStatutoryInterestRateRepository rateRepository, EmployeeRepository employeeRepository,
                                  CpfInterestComputationService interestComputationService) {
        this.ledgerRepository = ledgerRepository;
        this.runRepository = runRepository;
        this.rateRepository = rateRepository;
        this.employeeRepository = employeeRepository;
        this.interestComputationService = interestComputationService;
    }

    // ---------------------------------------------------------------- FY dashboard / dependency status

    @Transactional(readOnly = true)
    public List<CpfInterestFinancialYearStatusResponse> listFinancialYearStatuses() {
        String first = firstEligibleFinYear();
        String last = lastEligibleFinYear();
        List<CpfInterestFinancialYearStatusResponse> statuses = new ArrayList<>();
        for (String finYear = first; compareFinYear(finYear, last) <= 0; finYear = nextFinYear(finYear)) {
            statuses.add(buildYearStatus(finYear));
        }
        return statuses;
    }

    private CpfInterestFinancialYearStatusResponse buildYearStatus(String finYear) {
        BigDecimal rate = rateRepository.findByFinYearAndActiveTrue(finYear).map(CpfStatutoryInterestRate::getBaseCpfRate).orElse(null);
        Optional<CpfAnnualInterestRun> activeRun = runRepository.findByFinYearAndScopeAndStatusNotIn(finYear, CpfInterestRunScope.ALL_MEMBERS, INACTIVE_STATUSES);
        CpfInterestRunStatus status = activeRun.map(CpfAnnualInterestRun::getStatus).orElse(null);

        boolean postingAllowed = false;
        String blockedReason;
        if (status == null) {
            blockedReason = "Not yet calculated.";
        } else if (status == CpfInterestRunStatus.POSTED) {
            blockedReason = "Already posted.";
        } else {
            // CALCULATED
            if (rate == null) {
                blockedReason = "No notified CPF interest rate configured for this FY.";
            } else {
                Optional<String> sequentialBlock = sequentialBlockReason(finYear);
                if (sequentialBlock.isPresent()) {
                    blockedReason = sequentialBlock.get();
                } else if (activeRun.get().isDataReviewRequired()) {
                    blockedReason = "Data review required for one or more members before posting - see the run's member breakdown.";
                } else {
                    postingAllowed = true;
                    blockedReason = null;
                }
            }
        }

        return new CpfInterestFinancialYearStatusResponse(finYear, rate, true, true,
                activeRun.map(CpfAnnualInterestRun::getId).orElse(null), status, postingAllowed, blockedReason,
                activeRun.map(CpfAnnualInterestRun::getTotalMembersProcessed).orElse(0),
                activeRun.map(r -> r.getTotalInterestCreditedEe().add(r.getTotalInterestCreditedEr()).add(r.getTotalInterestCreditedVpf()))
                        .orElse(BigDecimal.ZERO),
                activeRun.map(CpfAnnualInterestRun::getPostedBy).map(Employee::getId).orElse(null),
                activeRun.map(CpfAnnualInterestRun::getPostedAt).orElse(null));
    }

    /** Empty when finYear is the first eligible FY, or its immediately preceding FY already has an active POSTED run; otherwise the reason posting must wait (Part 7 of the module spec). */
    private Optional<String> sequentialBlockReason(String finYear) {
        if (finYear.equals(firstEligibleFinYear())) {
            return Optional.empty();
        }
        String precedingFinYear = CpfRateResolutionService.precedingFinYear(finYear);
        boolean precedingPosted = runRepository
                .findByFinYearAndScopeAndStatusNotIn(precedingFinYear, CpfInterestRunScope.ALL_MEMBERS, INACTIVE_STATUSES)
                .map(r -> r.getStatus() == CpfInterestRunStatus.POSTED)
                .orElse(false);
        return precedingPosted ? Optional.empty() : Optional.of("Previous financial year interest is pending.");
    }

    /**
     * The earliest FY whose full opening-balance basis is confirmed to exist in the ledger - i.e. the FY
     * whose April-opening date (the preceding FY's March 31) is on or after the ledger's own earliest
     * value_date - AND whose full 12-month window is itself within the ledger's migrated span. Never
     * hardcoded - see this class's own javadoc.
     */
    String firstEligibleFinYear() {
        LocalDate earliest = ledgerRepository.findEarliestValueDate()
                .orElseThrow(() -> new BusinessRuleViolationException("No CPF ledger data exists yet - cannot determine any calculable financial year."));
        LocalDate latest = ledgerRepository.findLatestValueDate()
                .orElseThrow(() -> new BusinessRuleViolationException("No CPF ledger data exists yet - cannot determine any calculable financial year."));
        String candidate = CpfInterestCalculator.finYearFor(earliest);
        for (int guard = 0; guard < 200; guard++) {
            List<LocalDate> openingDates = CpfInterestCalculator.openingBalanceDatesFor(candidate);
            List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor(candidate);
            boolean openingBasisAvailable = !openingDates.get(0).isBefore(earliest);
            boolean fullYearAvailable = !monthEnds.get(monthEnds.size() - 1).isAfter(latest);
            if (openingBasisAvailable && fullYearAvailable) {
                return candidate;
            }
            candidate = nextFinYear(candidate);
        }
        throw new BusinessRuleViolationException("Could not determine a first eligible financial year from the current ledger span.");
    }

    /** The latest FY whose full 12-month April-March window is entirely within the ledger's migrated span (Part 19: a partial year, like FY2026-27 while data only reaches August 2026, is never eligible for a full-year run). */
    String lastEligibleFinYear() {
        LocalDate latest = ledgerRepository.findLatestValueDate()
                .orElseThrow(() -> new BusinessRuleViolationException("No CPF ledger data exists yet - cannot determine any calculable financial year."));
        String candidate = CpfInterestCalculator.finYearFor(latest);
        List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor(candidate);
        if (monthEnds.get(monthEnds.size() - 1).isAfter(latest)) {
            candidate = CpfRateResolutionService.precedingFinYear(candidate);
        }
        return candidate;
    }

    // ---------------------------------------------------------------- Calculate (read-only math, persisted preview)

    @Transactional
    public CpfInterestCalculationPreviewResponse calculatePreview(CpfInterestCalculateRequest request, Long calculatedByEmployeeId) {
        String finYear = request.finYear();
        validateFinYearEligible(finYear);
        CpfInterestRunScope scope = request.scope();
        if (scope == CpfInterestRunScope.SELECTED_MEMBER && request.employeeId() == null) {
            throw new BusinessRuleViolationException("employeeId is required when scope is SELECTED_MEMBER");
        }
        if (scope == CpfInterestRunScope.ALL_MEMBERS && request.employeeId() != null) {
            throw new BusinessRuleViolationException("employeeId must not be supplied when scope is ALL_MEMBERS");
        }

        CpfStatutoryInterestRate rateRow = rateRepository.findByFinYearAndActiveTrue(finYear)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No notified CPF interest rate configured for FY " + finYear + " - configure it under Interest Rate Configuration first."));
        BigDecimal rate = rateRow.getBaseCpfRate();

        Optional<CpfAnnualInterestRun> existingActive = scope == CpfInterestRunScope.ALL_MEMBERS
                ? runRepository.findByFinYearAndScopeAndStatusNotIn(finYear, CpfInterestRunScope.ALL_MEMBERS, INACTIVE_STATUSES)
                : runRepository.findByFinYearAndScopeAndMemberEmployee_IdAndStatusNotIn(finYear, CpfInterestRunScope.SELECTED_MEMBER,
                        request.employeeId(), INACTIVE_STATUSES);
        if (existingActive.isPresent()) {
            throw new BusinessRuleViolationException("An active interest run already exists for FY " + finYear + " (" + scope
                    + ", status " + existingActive.get().getStatus() + ") - reverse it before recalculating.");
        }

        List<Employee> targets = scope == CpfInterestRunScope.SELECTED_MEMBER
                ? List.of(employeeRepository.findById(request.employeeId())
                        .orElseThrow(() -> new BusinessRuleViolationException("Employee " + request.employeeId() + " not found")))
                : allMembersWithLedgerActivity();

        List<CpfInterestMemberBreakdown> breakdowns = new ArrayList<>();
        BigDecimal totalOpening = BigDecimal.ZERO, totalContrib = BigDecimal.ZERO, totalWithdraw = BigDecimal.ZERO;
        BigDecimal totalEe = BigDecimal.ZERO, totalEr = BigDecimal.ZERO, totalVpf = BigDecimal.ZERO;
        boolean anyDataReview = false;
        for (Employee employee : targets) {
            var result = calculateMonthlyProductResult(employee.getId(), finYear, rate);
            if (result.totalInterest().signum() <= 0 && scope == CpfInterestRunScope.ALL_MEMBERS) {
                continue; // No interest-bearing activity this FY - excluded from the run, matching the prior single-shot posting's own convention.
            }
            CpfInterestMemberBreakdown breakdown = buildMemberBreakdown(employee, finYear, result);
            breakdowns.add(breakdown);
            totalOpening = totalOpening.add(breakdown.openingBalance());
            totalContrib = totalContrib.add(breakdown.totalContributions());
            totalWithdraw = totalWithdraw.add(breakdown.totalWithdrawals());
            totalEe = totalEe.add(breakdown.eeInterest());
            totalEr = totalEr.add(breakdown.erInterest());
            totalVpf = totalVpf.add(breakdown.vpfInterest());
            anyDataReview = anyDataReview || breakdown.dataReviewRequired();
        }

        CpfAnnualInterestRun run = new CpfAnnualInterestRun(finYear, rate, request.interestOrderNo(), request.interestOrderDate());
        run.setScope(scope);
        if (scope == CpfInterestRunScope.SELECTED_MEMBER) {
            run.setMemberEmployee(targets.get(0));
        }
        run.setStatus(CpfInterestRunStatus.CALCULATED);
        run.setTotalMembersProcessed(breakdowns.size());
        run.setTotalInterestCreditedEe(totalEe.setScale(0, RoundingMode.HALF_UP));
        run.setTotalInterestCreditedEr(totalEr.setScale(0, RoundingMode.HALF_UP));
        run.setTotalInterestCreditedVpf(totalVpf.setScale(0, RoundingMode.HALF_UP));
        run.setDataReviewRequired(anyDataReview);
        run.setCalculatedAt(Instant.now());
        resolveOfficer(calculatedByEmployeeId).ifPresent(run::setCalculatedBy);
        run = runRepository.saveAndFlush(run);

        BigDecimal totalStatutory = totalEe.add(totalEr).add(totalVpf).setScale(0, RoundingMode.HALF_UP);
        return new CpfInterestCalculationPreviewResponse(run.getId(), finYear, scope, rate, breakdowns.size(),
                totalOpening, totalContrib, totalWithdraw,
                totalEe.setScale(2, RoundingMode.HALF_UP), totalEr.setScale(2, RoundingMode.HALF_UP), totalVpf.setScale(2, RoundingMode.HALF_UP),
                totalStatutory, anyDataReview, breakdowns);
    }

    /** Recomputes (never persists) the full member breakdown for an existing run - "View Calculation Details" (Part 15), always reflecting the ledger as it stands now rather than a snapshot that could drift stale between calculate and post. */
    @Transactional(readOnly = true)
    public List<CpfInterestMemberBreakdown> previewMembersForRun(Long runId) {
        CpfAnnualInterestRun run = findRunOrThrow(runId);
        List<CpfInterestMemberBreakdown> breakdowns = new ArrayList<>();
        for (Employee employee : resolveTargetEmployees(run)) {
            var result = calculateMonthlyProductResult(employee.getId(), run.getFinYear(), run.getDeclaredInterestRate());
            if (result.totalInterest().signum() <= 0 && run.getScope() == CpfInterestRunScope.ALL_MEMBERS) {
                continue;
            }
            breakdowns.add(buildMemberBreakdown(employee, run.getFinYear(), result));
        }
        return breakdowns;
    }

    // ---------------------------------------------------------------- Approve & Post

    @Transactional
    public CpfAnnualInterestRunResponse postRun(Long runId, boolean acknowledgeDataReview, Long postedByEmployeeId) {
        CpfAnnualInterestRun run = findRunOrThrow(runId);
        if (run.getStatus() != CpfInterestRunStatus.CALCULATED) {
            throw new BusinessRuleViolationException("Interest run " + runId + " is " + run.getStatus() + " - only a CALCULATED run can be posted.");
        }
        if (run.isDataReviewRequired() && !acknowledgeDataReview) {
            throw new BusinessRuleViolationException(
                    "Interest run " + runId + " has one or more members flagged DATA_REVIEW_REQUIRED - review the member breakdown and "
                            + "resubmit with acknowledgeDataReview=true to proceed.");
        }
        if (run.getScope() == CpfInterestRunScope.ALL_MEMBERS) {
            sequentialBlockReason(run.getFinYear()).ifPresent(reason -> {
                throw new BusinessRuleViolationException(reason);
            });
        }

        LocalDate valueDate = CpfInterestCalculator.monthEndsFor(run.getFinYear()).get(11); // the FY's own March 31
        BigDecimal rate = run.getDeclaredInterestRate();
        int membersProcessed = 0;
        BigDecimal totalEe = BigDecimal.ZERO, totalEr = BigDecimal.ZERO, totalVpf = BigDecimal.ZERO;

        // Recomputed fresh against the ledger as it stands right now (Part 17: "validate the calculation
        // run") rather than trusting whatever the calculate step's preview said, in case ledger activity
        // changed in between.
        for (Employee employee : resolveTargetEmployees(run)) {
            var result = calculateMonthlyProductResult(employee.getId(), run.getFinYear(), rate);
            if (result.totalInterest().signum() <= 0) {
                continue;
            }
            var priorEntry = interestComputationService.closingBalanceAsOf(employee.getId(), valueDate);
            BigDecimal priorEe = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningEeBalance).orElse(BigDecimal.ZERO);
            BigDecimal priorEr = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningErBalance).orElse(BigDecimal.ZERO);
            BigDecimal priorVpf = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningVpfBalance).orElse(BigDecimal.ZERO);

            interestComputationService.postHistoricalInterestEntry(employee, run.getFinYear(), valueDate, CpfLedgerEntryType.ANNUAL_INTEREST,
                    result, priorEe, priorEr, priorVpf, rate, run,
                    "Annual interest for FY " + run.getFinYear() + " (Order " + run.getInterestOrderNo() + ", run #" + runId + ")");
            interestComputationService.cascadeRunningBalanceOffset(employee.getId(), valueDate,
                    result.eeInterest().setScale(2, RoundingMode.HALF_UP), result.erInterest().setScale(2, RoundingMode.HALF_UP),
                    result.vpfInterest().setScale(2, RoundingMode.HALF_UP));

            membersProcessed++;
            totalEe = totalEe.add(result.eeInterest());
            totalEr = totalEr.add(result.erInterest());
            totalVpf = totalVpf.add(result.vpfInterest());
        }

        run.setTotalMembersProcessed(membersProcessed);
        run.setTotalInterestCreditedEe(totalEe.setScale(0, RoundingMode.HALF_UP));
        run.setTotalInterestCreditedEr(totalEr.setScale(0, RoundingMode.HALF_UP));
        run.setTotalInterestCreditedVpf(totalVpf.setScale(0, RoundingMode.HALF_UP));
        run.setStatus(CpfInterestRunStatus.POSTED);
        run.setPostedAt(Instant.now());
        resolveOfficer(postedByEmployeeId).ifPresent(run::setPostedBy);
        return CpfAnnualInterestRunResponse.from(run);
    }

    // ---------------------------------------------------------------- Reverse

    @Transactional
    public CpfAnnualInterestRunResponse reverseRun(Long runId, String reason, Long reversedByEmployeeId) {
        CpfAnnualInterestRun run = findRunOrThrow(runId);
        if (run.getStatus() != CpfInterestRunStatus.POSTED) {
            throw new BusinessRuleViolationException("Interest run " + runId + " is " + run.getStatus() + " - only a POSTED run can be reversed.");
        }

        LocalDate reversalDate = LocalDate.now();
        List<CpfTrustMemberLedgerEntry> postedEntries = ledgerRepository.findByInterestRun_IdAndEntryType(runId, CpfLedgerEntryType.ANNUAL_INTEREST);
        for (CpfTrustMemberLedgerEntry posted : postedEntries) {
            Employee employee = posted.getEmployee();
            BigDecimal eeAmount = posted.getEeShareCredit();
            BigDecimal erAmount = posted.getErShareCredit();
            BigDecimal vpfAmount = posted.getVpfCredit();

            var priorEntry = interestComputationService.closingBalanceAsOf(employee.getId(), reversalDate);
            BigDecimal priorEe = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningEeBalance).orElse(BigDecimal.ZERO);
            BigDecimal priorEr = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningErBalance).orElse(BigDecimal.ZERO);
            BigDecimal priorVpf = priorEntry.map(CpfTrustMemberLedgerEntry::getRunningVpfBalance).orElse(BigDecimal.ZERO);

            interestComputationService.postReversalEntry(employee, run.getFinYear(), reversalDate, eeAmount, erAmount, vpfAmount,
                    priorEe, priorEr, priorVpf, run, "Reversal of interest run #" + runId + " (FY " + run.getFinYear() + "): " + reason);
            interestComputationService.cascadeRunningBalanceOffset(employee.getId(), reversalDate,
                    eeAmount.negate(), erAmount.negate(), vpfAmount.negate());
        }

        run.setStatus(CpfInterestRunStatus.REVERSED);
        run.setReversedAt(Instant.now());
        resolveOfficer(reversedByEmployeeId).ifPresent(run::setReversedBy);
        run.setRemarks((run.getRemarks() == null ? "" : run.getRemarks() + " | ") + "REVERSED: " + reason);
        return CpfAnnualInterestRunResponse.from(run);
    }

    // ---------------------------------------------------------------- Lookups

    @Transactional(readOnly = true)
    public CpfAnnualInterestRunResponse getRun(Long runId) {
        return CpfAnnualInterestRunResponse.from(findRunOrThrow(runId));
    }

    @Transactional(readOnly = true)
    public List<CpfAnnualInterestRunResponse> listRuns() {
        return runRepository.findAllByOrderByFinYearDescCalculatedAtDesc().stream().map(CpfAnnualInterestRunResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CpfAnnualInterestRunResponse> memberRunHistory(Long employeeId) {
        return runRepository.findByMemberEmployee_IdOrderByFinYearDesc(employeeId).stream().map(CpfAnnualInterestRunResponse::from).toList();
    }

    // ---------------------------------------------------------------- Shared internals

    /** Null-safe alternative to EmployeeRepository.findById(), which throws IllegalArgumentException on a null id (this app's "acting officer" is optional at every one of this class's write endpoints - see CpfInterestComputationService.computeAnnualInterest's own precedent). */
    private Optional<Employee> resolveOfficer(Long employeeId) {
        return employeeId == null ? Optional.empty() : employeeRepository.findById(employeeId);
    }

    private CpfAnnualInterestRun findRunOrThrow(Long runId) {
        return runRepository.findById(runId).orElseThrow(() -> new BusinessRuleViolationException("Interest run " + runId + " not found"));
    }

    private List<Employee> resolveTargetEmployees(CpfAnnualInterestRun run) {
        if (run.getScope() == CpfInterestRunScope.SELECTED_MEMBER) {
            return List.of(run.getMemberEmployee());
        }
        return allMembersWithLedgerActivity();
    }

    private List<Employee> allMembersWithLedgerActivity() {
        return ledgerRepository.findDistinctEmployeeIds().stream().map(employeeRepository::getReferenceById).toList();
    }

    private CpfInterestCalculator.CpfInterestCalculationResult calculateMonthlyProductResult(Long employeeId, String finYear, BigDecimal rate) {
        BigDecimal monthlyProductEe = BigDecimal.ZERO, monthlyProductEr = BigDecimal.ZERO, monthlyProductVpf = BigDecimal.ZERO;
        for (LocalDate openingDate : CpfInterestCalculator.openingBalanceDatesFor(finYear)) {
            var closing = interestComputationService.closingBalanceAsOf(employeeId, openingDate);
            if (closing.isPresent()) {
                monthlyProductEe = monthlyProductEe.add(closing.get().getRunningEeBalance());
                monthlyProductEr = monthlyProductEr.add(closing.get().getRunningErBalance());
                monthlyProductVpf = monthlyProductVpf.add(closing.get().getRunningVpfBalance());
            }
        }
        return CpfInterestCalculator.calculate(monthlyProductEe, monthlyProductEr, monthlyProductVpf, rate, 12, finYear,
                CpfInterestCalculator.CalculationMode.FULL_YEAR);
    }

    /**
     * Part 9/31 of the module spec: an employee who joined before this FY's own opening-balance date but
     * has no ledger row on/before that date has an unconfirmed pre-migration opening balance - flag rather
     * than silently treating it as zero. Only the two legacy members migrated with an explicit
     * OPENING_BALANCE row (see V74) predate the ledger's earliest PAYROLL_MONTHLY activity on the current
     * dataset; every other member's first ledger row is presumed to be their genuine JCI joining activity,
     * not a missing-migration gap - this check exists to catch the case where that presumption is wrong,
     * not to assert it is always right.
     */
    private boolean isDataReviewRequired(Employee employee, LocalDate openingDate) {
        LocalDate joinDate = employee.getDateOfJoining();
        if (joinDate == null || !joinDate.isBefore(openingDate)) {
            return false;
        }
        return interestComputationService.closingBalanceAsOf(employee.getId(), openingDate).isEmpty();
    }

    /**
     * Part 10/11/25 of the module spec: if a member's earliest ledger row's own recorded running total
     * doesn't match the sum of its EE/ER/VPF components, that member's legacy opening allocation is
     * internally inconsistent (a migration-time anomaly, not something this module corrects - see
     * CpfTrustMemberLedgerEntry's own "never updated in place" contract). Detected generically from the
     * ledger's own EE+ER+VPF=Total invariant rather than hardcoded to a specific employee id, so it
     * surfaces for whichever member actually has it.
     */
    private Optional<String> legacyComponentAnomalyMessage(Long employeeId) {
        List<CpfTrustMemberLedgerEntry> rows = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employeeId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        CpfTrustMemberLedgerEntry earliest = rows.get(0);
        BigDecimal componentSum = earliest.getRunningEeBalance().add(earliest.getRunningErBalance()).add(earliest.getRunningVpfBalance());
        if (componentSum.subtract(earliest.getRunningTotalBalance()).abs().compareTo(new BigDecimal("0.02")) <= 0) {
            return Optional.empty();
        }
        return Optional.of("Legacy EE/ER/VPF component allocation anomaly exists on this member's earliest ledger row (recorded component "
                + "balances do not sum to its recorded total balance). Historical ledger values are preserved unmodified; interest is "
                + "calculated per-component from the EE/ER/VPF running balances actually on record.");
    }

    private CpfInterestMemberBreakdown buildMemberBreakdown(Employee employee, String finYear, CpfInterestCalculator.CpfInterestCalculationResult result) {
        LocalDate openingDate = CpfInterestCalculator.openingBalanceDatesFor(finYear).get(0);
        var openingRow = interestComputationService.closingBalanceAsOf(employee.getId(), openingDate);
        BigDecimal openingBalance = openingRow
                .map(e -> e.getRunningEeBalance().add(e.getRunningErBalance()).add(e.getRunningVpfBalance()))
                .orElse(BigDecimal.ZERO);

        BigDecimal contributions = BigDecimal.ZERO;
        BigDecimal withdrawals = BigDecimal.ZERO;
        for (CpfTrustMemberLedgerEntry row : ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), finYear)) {
            contributions = contributions.add(row.getEeShareCredit()).add(row.getErShareCredit()).add(row.getVpfCredit())
                    .add(row.getLoanRepayPrincipal()).add(row.getLoanRepayInterest());
            withdrawals = withdrawals.add(row.getEeShareDebit()).add(row.getErShareDebit()).add(row.getVpfDebit());
        }

        boolean dataReviewRequired = isDataReviewRequired(employee, openingDate);
        String dataReviewReason = dataReviewRequired
                ? "Employee joined " + employee.getDateOfJoining() + ", before this FY's opening-balance date " + openingDate
                        + ", but no CPF ledger row exists on/before that date - the pre-migration opening balance could not be confirmed."
                : null;

        Optional<String> anomaly = legacyComponentAnomalyMessage(employee.getId());

        BigDecimal eeInterest = result.eeInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal erInterest = result.erInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal vpfInterest = result.vpfInterest().setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedClosing = openingBalance.add(contributions).subtract(withdrawals).add(result.totalInterest());

        return new CpfInterestMemberBreakdown(employee.getId(), employee.getEmployeeCode(),
                (employee.getFirstName() + " " + employee.getLastName()).trim(),
                openingBalance, contributions, withdrawals, eeInterest, erInterest, vpfInterest, result.totalInterest(), projectedClosing,
                dataReviewRequired, dataReviewReason, anomaly.isPresent(), anomaly.orElse(null));
    }

    private void validateFinYearEligible(String finYear) {
        String first = firstEligibleFinYear();
        String last = lastEligibleFinYear();
        if (compareFinYear(finYear, first) < 0 || compareFinYear(finYear, last) > 0) {
            throw new BusinessRuleViolationException("FY " + finYear + " is outside the calculable range [" + first + " .. " + last
                    + "] established from the migrated ledger's own earliest/latest transaction dates.");
        }
    }

    private static int compareFinYear(String a, String b) {
        return Integer.parseInt(a.substring(0, 4)) - Integer.parseInt(b.substring(0, 4));
    }

    private static String nextFinYear(String finYear) {
        int startYear = Integer.parseInt(finYear.substring(0, 4)) + 1;
        return startYear + "-" + (startYear + 1);
    }
}
