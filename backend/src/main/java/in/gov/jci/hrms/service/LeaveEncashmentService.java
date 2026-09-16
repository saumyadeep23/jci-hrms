package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EncashmentGateDecisionRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentHistoryResponse;
import in.gov.jci.hrms.dto.LeaveEncashmentRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.CareerEventType;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-service EL encashment two-gate workflow (PIMS ALMS Phase 2, Section 4):
 * apply() reserves encashable_reserved; hrApprove() (Gate 1) and
 * financeApprove() (Gate 2) must both APPROVE, in that order, before the
 * final debit/payroll-eligible/service-book write happens. Rejecting at
 * either gate releases the reservation - the application itself is never
 * deleted, only marked REJECTED at whichever gate declined it.
 */
@Service
@Transactional(readOnly = true)
public class LeaveEncashmentService {

    private static final String EL_CODE = "EL";
    private static final BigDecimal IN_SERVICE_MINIMUM_DAYS = new BigDecimal("15.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter NARRATIVE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final LeaveEncashmentApplicationRepository encashmentRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final EmployeeServiceBookRepository serviceBookRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;

    public LeaveEncashmentService(LeaveEncashmentApplicationRepository encashmentRepository,
                                   LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                                   LeaveLedgerEntryRepository leaveLedgerEntryRepository,
                                   EmployeeServiceBookRepository serviceBookRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveTypeRepository leaveTypeRepository,
                                   RegularPayFixationRepository regularPayFixationRepository,
                                   DaRateHistoryRepository daRateHistoryRepository) {
        this.encashmentRepository = encashmentRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.serviceBookRepository = serviceBookRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
    }

    /**
     * Admin review (Gate 1/Gate 2 queues): one query for every application (with employee +
     * designation JOIN FETCHed), plus one batched query for every employee's current basic pay -
     * never one query per row for either.
     */
    public List<LeaveEncashmentResponse> listForAdminReview() {
        List<LeaveEncashmentApplication> applications = encashmentRepository.findAllWithEmployeeAndDesignation();
        List<Long> employeeIds = applications.stream().map(a -> a.getEmployee().getId()).distinct().toList();
        Map<Long, BigDecimal> basicPayByEmployeeId = regularPayFixationRepository.findByEmployeeIdInAndCurrentTrue(employeeIds).stream()
                .collect(Collectors.toMap(rpf -> rpf.getEmployee().getId(), RegularPayFixation::getBasicPay, (first, second) -> first));
        return applications.stream()
                .map(a -> LeaveEncashmentResponse.from(a, basicPayByEmployeeId.get(a.getEmployee().getId())))
                .toList();
    }

    /**
     * Every encashment application (any gate status) for one employee, enriched with their current
     * basic pay - used both by the EL ledger modal's "under process" hold rows (which don't display
     * basic pay, but there's no harm resolving it) and by mine()/self-service, whose claim cards
     * need it for the "Gross Amount (Basic: ... | DA: ...%)" pill. A single lookup, not the batched
     * one listForAdminReview() uses, since this is already scoped to one employee.
     */
    public List<LeaveEncashmentResponse> listByEmployee(Long employeeId) {
        BigDecimal basicPay = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employeeId)
                .map(RegularPayFixation::getBasicPay)
                .orElse(null);
        return encashmentRepository.findByEmployeeId(employeeId).stream()
                .map(a -> LeaveEncashmentResponse.from(a, basicPay))
                .toList();
    }

    /**
     * Month/year-wise sanction history for HR/Finance audit - only applications whose two-gate
     * workflow has actually finished (either fully approved - "SANCTIONED" - or rejected at either
     * gate), filtered on the decision date (Finance's decision, or HR's if HR itself rejected) in
     * IST, matching this codebase's other date-in-IST conventions. month null/0 returns the whole
     * year. There is no separate voucher-numbering system in this schema, so voucherRefNo is a
     * synthesized "ELE-{id}" display value, not a persisted field - flagged here so it's never
     * mistaken for a real finance-issued reference.
     */
    public List<LeaveEncashmentHistoryResponse> listHistory(int year, Integer month) {
        List<LeaveEncashmentApplication> applications = encashmentRepository.findAllWithEmployeeAndDesignation();
        List<Long> employeeIds = applications.stream().map(a -> a.getEmployee().getId()).distinct().toList();
        Map<Long, BigDecimal> basicPayByEmployeeId = regularPayFixationRepository.findByEmployeeIdInAndCurrentTrue(employeeIds).stream()
                .collect(Collectors.toMap(rpf -> rpf.getEmployee().getId(), RegularPayFixation::getBasicPay, (first, second) -> first));

        return applications.stream()
                .filter(this::isFinalized)
                .filter(a -> matchesYearMonth(decisionDate(a), year, month))
                .map(a -> LeaveEncashmentHistoryResponse.from(a, basicPayByEmployeeId.get(a.getEmployee().getId()), resolveStatus(a)))
                .toList();
    }

    private boolean isFinalized(LeaveEncashmentApplication application) {
        if (application.getHrApprovalStatus() == ApprovalStatus.PENDING) {
            return false;
        }
        if (application.getHrApprovalStatus() == ApprovalStatus.REJECTED) {
            return true;
        }
        return application.getFinanceApprovalStatus() != ApprovalStatus.PENDING;
    }

    private String resolveStatus(LeaveEncashmentApplication application) {
        if (application.getHrApprovalStatus() == ApprovalStatus.REJECTED
                || application.getFinanceApprovalStatus() == ApprovalStatus.REJECTED) {
            return "REJECTED";
        }
        return "SANCTIONED";
    }

    private Instant decisionDate(LeaveEncashmentApplication application) {
        return application.getFinanceApprovedAt() != null ? application.getFinanceApprovedAt() : application.getHrApprovedAt();
    }

    private boolean matchesYearMonth(Instant decisionInstant, int year, Integer month) {
        if (decisionInstant == null) {
            return false;
        }
        LocalDate decisionDate = decisionInstant.atZone(IST).toLocalDate();
        if (decisionDate.getYear() != year) {
            return false;
        }
        return month == null || month == 0 || decisionDate.getMonthValue() == month;
    }

    /** SEC-006 remediation (docs/security/SEC_001_002_REMEDIATION.md pattern): same defense-in-depth shape as MobilePunchService.create()/AttendanceRegularizationService.submit(). */
    @Transactional
    public LeaveEncashmentResponse apply(LeaveEncashmentRequest request, Long callerEmployeeId, boolean onBehalfOfOthersPermitted) {
        if (!onBehalfOfOthersPermitted && !request.employeeId().equals(callerEmployeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Not authorized to apply for leave encashment for employee " + request.employeeId() + " on behalf of another employee");
        }
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        if (request.encashmentType() == EncashmentType.IN_SERVICE_EL) {
            enforceOncePerCalendarYear(employee, LocalDate.now().getYear());

            if (request.elDaysClaimed().compareTo(IN_SERVICE_MINIMUM_DAYS) < 0) {
                throw new BusinessRuleViolationException(
                        "In-service EL encashment requires at least " + IN_SERVICE_MINIMUM_DAYS + " days claimed");
            }
            if (request.hplDaysClaimed() != null && request.hplDaysClaimed().compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessRuleViolationException("In-service EL encashment cannot include HPL days");
            }
        }

        LeaveEntitlementBalance balance = resolveElBalance(employee.getId(), LocalDate.now().getYear());
        if (request.elDaysClaimed().compareTo(balance.getEncashableAvailable()) > 0) {
            throw new InsufficientLeaveBalanceException(
                    "Insufficient encashable EL balance: available " + balance.getEncashableAvailable()
                            + ", claimed " + request.elDaysClaimed());
        }

        balance.setEncashableReserved(balance.getEncashableReserved().add(request.elDaysClaimed()));
        balance.setEncashableAvailable(balance.getEncashableAvailable().subtract(request.elDaysClaimed()));
        balance.setAvailableBalance(balance.getAvailableBalance().subtract(request.elDaysClaimed()));
        entitlementBalanceRepository.saveAndFlush(balance);

        LeaveEncashmentApplication application = new LeaveEncashmentApplication(
                employee, request.encashmentType(), request.elDaysClaimed(), request.hplDaysClaimed());
        applyEmolumentsSnapshot(application, employee, request.elDaysClaimed(), LocalDate.now());
        return LeaveEncashmentResponse.from(encashmentRepository.saveAndFlush(application));
    }

    /**
     * Statutory once-per-calendar-year rule for in-service EL encashment: an employee may not have
     * more than one not-yet-rejected IN_SERVICE_EL application in the same calendar year (by
     * created_at, in IST - there is no separate "applied date" column). SUPERANNUATION/SEPARATION
     * encashments are one-time terminal events tied to leaving service, not a recurring yearly
     * claim, so this rule is scoped to IN_SERVICE_EL only - see the caller.
     */
    private void enforceOncePerCalendarYear(Employee employee, int year) {
        encashmentRepository.findByEmployeeId(employee.getId()).stream()
                .filter(a -> a.getEncashmentType() == EncashmentType.IN_SERVICE_EL)
                .filter(a -> a.getHrApprovalStatus() != ApprovalStatus.REJECTED)
                .filter(a -> a.getFinanceApprovalStatus() != ApprovalStatus.REJECTED)
                .filter(a -> a.getCreatedAt().atZone(IST).getYear() == year)
                .findFirst()
                .ifPresent(existing -> {
                    throw new BusinessRuleViolationException(String.format(
                            "In-service Earned Leave encashment can only be availed once in a calendar year. "
                                    + "A claim (ELE-%d) is already active or sanctioned for %d.",
                            existing.getId(), year));
                });
    }

    /**
     * CPSE formula: Monthly Emoluments = Basic + DA; Daily Emoluments = that / 30; Gross = Daily *
     * days claimed. Snapshotted once here (apply() time) rather than recomputed on every read, so
     * a later DA revision doesn't silently change what an already-submitted claim quotes - see
     * checkForRetroactiveArrear() for how a genuine retroactive hike is handled instead.
     */
    private void applyEmolumentsSnapshot(LeaveEncashmentApplication application, Employee employee,
                                          BigDecimal daysClaimed, LocalDate asOfDate) {
        RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employee.getId() + " has no current pay fixation; cannot compute encashment emoluments"));
        GradeScaleMaster gradeScale = fixation.getGradeScale();
        DaRateHistory daRate = daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(gradeScale.getScaleType(), asOfDate)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active DA rate configured for scale type " + gradeScale.getScaleType() + " as of " + asOfDate));

        application.setDaRateApplied(daRate.getDaPercentage());
        application.setDaEffectiveDate(daRate.getEffectiveFrom());
        application.setGrossAmount(computeGrossAmount(fixation.getBasicPay(), daRate.getDaPercentage(), daysClaimed));
    }

    private BigDecimal computeGrossAmount(BigDecimal basicPay, BigDecimal daPercentage, BigDecimal daysClaimed) {
        BigDecimal monthlyEmoluments = basicPay.add(basicPay.multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal dailyEmoluments = monthlyEmoluments.divide(DAYS_PER_MONTH, 10, RoundingMode.HALF_UP);
        return dailyEmoluments.multiply(daysClaimed).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public LeaveEncashmentResponse hrApprove(Long id, EncashmentGateDecisionRequest decision, Long approverEmployeeId) {
        LeaveEncashmentApplication application = findOrThrow(id);
        requireGateStatus(application.getHrApprovalStatus(), "HR");

        if (Boolean.TRUE.equals(decision.approve())) {
            application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        } else {
            application.setHrApprovalStatus(ApprovalStatus.REJECTED);
            releaseReservation(application);
        }
        application.setHrApprovedBy(resolveApprover(approverEmployeeId));
        application.setHrApprovedAt(Instant.now());
        application.setHrRemarks(decision.remarks());
        return LeaveEncashmentResponse.from(application);
    }

    @Transactional
    public LeaveEncashmentResponse financeApprove(Long id, EncashmentGateDecisionRequest decision, Long approverEmployeeId) {
        LeaveEncashmentApplication application = findOrThrow(id);
        if (application.getHrApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessRuleViolationException(
                    "Encashment application " + id + " requires HR (Gate 1) approval before Finance (Gate 2) can act");
        }
        requireGateStatus(application.getFinanceApprovalStatus(), "Finance");

        if (Boolean.TRUE.equals(decision.approve())) {
            application.setFinanceApprovalStatus(ApprovalStatus.APPROVED);
            finalizeEncashment(application);
        } else {
            application.setFinanceApprovalStatus(ApprovalStatus.REJECTED);
            releaseReservation(application);
        }
        application.setFinanceApprovedBy(resolveApprover(approverEmployeeId));
        application.setFinanceApprovedAt(Instant.now());
        application.setFinanceRemarks(decision.remarks());
        return LeaveEncashmentResponse.from(application);
    }

    /** Gate 2 approval: debit encashable_current, mark payroll-eligible, and write the digital-service-book entry - all in the same transaction as the Finance decision itself. */
    private void finalizeEncashment(LeaveEncashmentApplication application) {
        LeaveEntitlementBalance balance = resolveElBalance(application.getEmployee().getId(), LocalDate.now().getYear());
        BigDecimal amount = application.getElDaysClaimed();

        balance.setEncashableReserved(balance.getEncashableReserved().subtract(amount));
        balance.setEncashableCurrent(balance.getEncashableCurrent().subtract(amount));
        balance.setEncashableEncashed(balance.getEncashableEncashed().add(amount));
        balance.setEncashedDays(balance.getEncashedDays().add(amount));
        balance.setCurrentBalance(balance.getCurrentBalance().subtract(amount));
        entitlementBalanceRepository.saveAndFlush(balance);

        checkForRetroactiveArrear(application);

        LeaveLedgerEntry entry = new LeaveLedgerEntry(application.getEmployee(), balance.getLeaveType(), LocalDate.now(),
                amount.negate(), "EL encashment debit (" + application.getEncashmentType() + ") - application " + application.getId(),
                LeaveLedgerSource.EL_ENCASHMENT_DEBIT);
        leaveLedgerEntryRepository.saveAndFlush(entry);

        application.setPayrollEligible(true);
        application.setServiceBookEntry(writeSanctionServiceBookEntry(application));
    }

    /**
     * Structured statutory e-Service Book record for the sanction itself (a second, separate entry
     * gets written later - see recordArrearClearance() - only if/when an arrear is actually disbursed
     * through payroll). event_type is kept as the pre-existing EARNED_LEAVE_ENCASHABLE label, not a
     * new "LEAVE_ENCASHMENT" one, deliberately: event_type is free-text (no enum/CHECK constraint - see
     * that entity's own javadoc), and introducing a second label for the same event would fragment any
     * future report that filters or groups on it.
     */
    private EmployeeServiceBook writeSanctionServiceBookEntry(LeaveEncashmentApplication application) {
        Employee employee = application.getEmployee();
        LocalDate sanctionDate = LocalDate.now();
        String sanctionRef = "ELE-" + application.getId();
        BigDecimal basicPay = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .map(RegularPayFixation::getBasicPay)
                .orElse(null);
        BigDecimal daRate = application.getDaRateApplied();
        BigDecimal daAmount = basicPay != null && daRate != null
                ? basicPay.multiply(daRate).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                : null;

        String narrative = String.format(
                "Sanctioned in-service encashment of %s days of Earned Leave. Gross Amount: Rs.%s "
                        + "(Basic Pay: Rs.%s, DA @ %s%%: Rs.%s). Sanction Ref: %s dated %s.",
                application.getElDaysClaimed(), application.getGrossAmount(), basicPay, daRate, daAmount,
                sanctionRef, NARRATIVE_DATE_FORMAT.format(sanctionDate));

        EmployeeServiceBook serviceBookEntry = new EmployeeServiceBook(employee, sanctionDate, CareerEventType.EARNED_LEAVE_ENCASHABLE.name());
        serviceBookEntry.setOrderNumber(sanctionRef);
        serviceBookEntry.setOrderDate(sanctionDate);
        serviceBookEntry.setBasicPay(basicPay);
        serviceBookEntry.setDaysEncashed(application.getElDaysClaimed());
        serviceBookEntry.setDaRate(daRate);
        serviceBookEntry.setGrossAmount(application.getGrossAmount());
        // eventDescription is left null (not the old "Leave Account / Encashments" placeholder) -
        // both service-book views derive a dynamic "{days} Days Earned Leave Encashment" title from
        // daysEncashed instead, and render this structured narrative from remarks in full.
        serviceBookEntry.setRemarks(narrative);
        serviceBookEntry.setMigrated(false);
        return serviceBookRepository.saveAndFlush(serviceBookEntry);
    }

    /** Written once an arrear actually clears payroll (PayrollRunService.finalizeRun()) - references the original sanction so the two entries read as one continuous record. */
    void recordArrearClearance(LeaveEncashmentApplication application, int payrollCycleYear, int payrollCycleMonth) {
        String sanctionRef = "ELE-" + application.getId();
        LocalDate clearanceDate = LocalDate.now();
        String narrative = String.format(
                "DA arrear of Rs.%s cleared against Sanction Ref: %s (in-service EL encashment, %s days) - disbursed in payroll cycle %d-%02d.",
                application.getArrearAmount(), sanctionRef, application.getElDaysClaimed(), payrollCycleYear, payrollCycleMonth);

        EmployeeServiceBook entry = new EmployeeServiceBook(application.getEmployee(), clearanceDate, CareerEventType.EARNED_LEAVE_ENCASHABLE.name());
        entry.setOrderNumber(sanctionRef);
        entry.setOrderDate(clearanceDate);
        entry.setDaysEncashed(application.getElDaysClaimed());
        entry.setGrossAmount(application.getArrearAmount());
        entry.setEventDescription("Leave Account / Encashments - Arrear Clearance");
        entry.setRemarks(narrative);
        entry.setMigrated(false);
        serviceBookRepository.saveAndFlush(entry);
    }

    /**
     * A DA rate revised upward with retroactive effect between apply() and Finance's sanction is a
     * real, expected occurrence (DA hikes are routinely notified after their effective date) - this
     * tops up what the claim owes rather than silently paying the stale, now-understated rate.
     * Never fires backward (a lower current rate than daRateApplied is left alone - the employee
     * keeps the rate they were quoted, they're never charged a retroactive cut).
     */
    private void checkForRetroactiveArrear(LeaveEncashmentApplication application) {
        if (application.getDaRateApplied() == null) {
            return; // pre-V63 rows with no snapshot to compare against
        }
        RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(application.getEmployee().getId())
                .orElse(null);
        if (fixation == null) {
            return;
        }
        Optional<DaRateHistory> currentDaRate = daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        fixation.getGradeScale().getScaleType(), LocalDate.now());
        if (currentDaRate.isEmpty() || currentDaRate.get().getDaPercentage().compareTo(application.getDaRateApplied()) <= 0) {
            return;
        }

        BigDecimal rateDelta = currentDaRate.get().getDaPercentage().subtract(application.getDaRateApplied());
        BigDecimal dailyDelta = fixation.getBasicPay().multiply(rateDelta).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .divide(DAYS_PER_MONTH, 10, RoundingMode.HALF_UP);
        application.setArrearAmount(dailyDelta.multiply(application.getElDaysClaimed()).setScale(2, RoundingMode.HALF_UP));
        application.setArrearDaRateDiff(rateDelta);
        application.setArrearSettled(false);
    }

    private void releaseReservation(LeaveEncashmentApplication application) {
        LeaveEntitlementBalance balance = resolveElBalance(application.getEmployee().getId(), LocalDate.now().getYear());
        balance.setEncashableReserved(balance.getEncashableReserved().subtract(application.getElDaysClaimed()));
        balance.setEncashableAvailable(balance.getEncashableAvailable().add(application.getElDaysClaimed()));
        balance.setAvailableBalance(balance.getAvailableBalance().add(application.getElDaysClaimed()));
        entitlementBalanceRepository.saveAndFlush(balance);
    }

    private void requireGateStatus(ApprovalStatus status, String gateName) {
        if (status != ApprovalStatus.PENDING) {
            throw new BusinessRuleViolationException(gateName + " gate has already been decided (" + status + ")");
        }
    }

    private Employee resolveApprover(Long approverEmployeeId) {
        if (approverEmployeeId == null) {
            return null;
        }
        return employeeRepository.findById(approverEmployeeId).orElse(null);
    }

    private LeaveEntitlementBalance resolveElBalance(Long employeeId, int year) {
        LeaveType el = leaveTypeRepository.findByCode(EL_CODE)
                .orElseThrow(() -> new BusinessRuleViolationException("EL leave type is not configured"));
        return entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, el.getId(), year)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No EL entitlement balance provisioned for employee " + employeeId + ", year " + year));
    }

    private LeaveEncashmentApplication findOrThrow(Long id) {
        return encashmentRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException("Leave Encashment Application", id));
    }
}
