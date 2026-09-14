package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PayrollMonthlyHeadItemResponse;
import in.gov.jci.hrms.dto.PayrollMonthlyRecordResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeCeaClaim;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import in.gov.jci.hrms.entity.EmployeeSuspensionNec;
import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.DeputationStatus;
import in.gov.jci.hrms.entity.PayOption;
import in.gov.jci.hrms.entity.SuspensionStatus;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;
import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.PayrollMovementInput;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.entity.PtaxSlab;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.TransportAllowanceRate;
import in.gov.jci.hrms.entity.VehicleAllotmentStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.PayrollTaxOverrideRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.TransportAllowanceRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The unified Payroll Computation Engine's orchestrator - Earnings, Deductions, HRA Suppression,
 * Vehicle Entitlement, Head 20 Leave Encashment (25th cutoff), Remote Area Allowance, mid-month
 * pay-fixation/office-transfer splitting, and Section 192 TDS, staged into payroll_batches/
 * payroll_monthly_records/payroll_monthly_head_items. Deliberately a separate class from the
 * pre-existing {@link PayrollComputationService} (Phase 5, SRS 4.4/4.4a - a different, still-active
 * cycle-based engine keyed on payroll_runs/payslips) rather than a rewrite of it - the two target
 * genuinely different schemas with their own independent lifecycles; see this class's own javadoc on
 * payrollBatch/payrollRun for how leave-encashment tracking keeps the two from colliding.
 *
 * <p>Head numbers used here are the REAL payroll_salary_heads catalog (V66/V69 seed), not the literal
 * numbers named in some product-brief drafts of this feature - notably DA is head 7 (CDA) or 8 (IDA)
 * depending on pay pattern, not head 2 (which is DAYS_PRESENT, a CASUAL-only no-effect head), and
 * Professional Tax is head 49, not head 42 (RECREATION CLUB). Posting under the brief's literal but
 * wrong numbers would silently mislabel real deductions, so the actual catalog wins.
 *
 * <h2>Mid-month splitting</h2>
 * A month's Earnings (Basic/DA/HRA/Transport/Remote Area Allowance) are computed as a sum of
 * {@link EarningsSlice}s, not a single whole-month figure - each slice is the day-range where BOTH
 * "which {@link RegularPayFixation} rate applies" (a promotion effective mid-month) AND "which office
 * the employee is paid against" (a transfer mid-month, via {@link PayrollMovementInput}) are constant.
 * This is what makes a same-month promotion+transfer (e.g. released from the old office, a few days'
 * Joining Time, then joining the new office at a new, promoted basic) compute correctly instead of
 * applying whichever RegularPayFixation happens to be "current" by computation time to the whole month.
 * See {@link #buildOfficeWindows} and {@link #buildEarningsSlices} for the mechanics, and
 * {@link #resolveRemoteAreaRate} for the new Head 21 consumer of state_master's remote-area fields
 * (previously seeded but read by no payroll code anywhere).
 *
 * <p>Deductions (CPF/NPS/Car-use/Accommodation/TDS) deliberately stay whole-month, non-segmented - they
 * depend on the month's TOTAL gross/Basic+DA, not on which office/fixation earned which rupee, so there
 * is nothing to split there. P-Tax is the one exception with a segment dependency: it resolves off the
 * employee's EFFECTIVE month-end station (the last {@link EarningsSlice} chronologically - the
 * receiving office after a mid-month transfer), not the whole month, since P-Tax is a where-you-are-
 * posted-now tax, not a where-you-earned-each-rupee one. See {@link #resolvePtax}.
 *
 * <p>Other simplifications made explicit rather than silently assumed (no richer data model exists to
 * do better):
 * <ul>
 *   <li>"Active employees eligible for payroll" = {@link EmployeeStatus#ACTIVE} with a current
 *   {@link RegularPayFixation} - there is no is_salary_held column on employees (only on
 *   payroll_monthly_records, which is this method's own output), so it cannot be a pre-filter; an
 *   ACTIVE employee with no current fixation is skipped (logged), not failed as a whole-batch error,
 *   so one bad record doesn't block the rest of the batch.</li>
 *   <li>When a month has a {@link PayrollMovementInput}, that row's own day-split (releasing office /
 *   Joining Time / transit-LWP / receiving office) is treated as authoritative for the WHOLE month, and
 *   the ordinary DailyAttendance-based LOP check is skipped entirely for that employee that month - a
 *   real attendance register does not span two offices' registers within one transfer month. Joining
 *   Time itself is paid at the RELEASING office's terms (city class/rate), matching the FR/SR
 *   convention that JT pay/allowances are those admissible immediately before relinquishing the old
 *   charge.</li>
 *   <li>desgn_code has no dedicated code column on Designation (only a free-text title) - this uses
 *   the title itself as the persisted desgn_code.</li>
 *   <li>P-Tax and Remote Area Allowance state resolution goes through RegionalOffice.getState() (a
 *   free-text state name) resolved to state_master.state_code / state_master row via a case-insensitive
 *   name match - RegionalOffice has no direct state_code FK. An office whose name doesn't resolve has
 *   no P-Tax/Remote-Area line rather than a hard failure.</li>
 *   <li>DA is rounded once per scale type on that scale's TOTAL Earned Basic for the month (see
 *   compute()'s own comment) - HRA and Remote Area Allowance, by contrast, are each rounded per slice
 *   and summed, since those genuinely are a percentage of what that specific slice earned. Transport
 *   Allowance is a flat monthly rate, not proportional to Basic at all, so a Joining Time slice simply
 *   contributes none rather than a differently-rounded amount.</li>
 *   <li>NPS employer-exemption eligibility passed into the TDS engine is employee.isNpsEligible() -
 *   the same flag PayrollTdsEngine's own javadoc names as the assumed proxy - AND-ed with hasPran(): a
 *   declaration can exist before the employee's PRAN is actually on file (they're separate processes),
 *   and neither the Head 62 employee deduction (resolveNps()) nor this employer exemption estimate may
 *   fire until it is - see resolveNps()'s own javadoc.</li>
 *   <li>employee.getRegionalOffice() used to never be updated by the movement lifecycle - a movement
 *   month always computed correctly off PayrollMovementInput regardless, but a FOLLOWING month with
 *   no PayrollMovementInput row would keep using whatever office was on the employee record until HR
 *   updated it there by hand. JoiningReportService.syncPostIncumbencyAndMasterData() (fired on joining
 *   approval) now closes this: whenever a movement's destination resolves to exactly one sanctioned
 *   PostMaster, the employee's department/designation/regionalOffice/DPC are synced from it, so a
 *   following month's whole-month fallback window in buildOfficeWindows() reads the correct office.
 *   Ambiguous destinations (0 or &gt;1 matching posts) still skip that sync - see this method's own
 *   javadoc there - so the gap can still resurface for those cases.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PayrollBatchComputationService {

    private static final Logger log = LoggerFactory.getLogger(PayrollBatchComputationService.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");
    private static final BigDecimal DEFAULT_CPF_EMPLOYEE_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_CAR_USE_DEDUCTION = new BigDecimal("2000.00");
    private static final BigDecimal DEFAULT_GIS_FLAT_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal DEFAULT_LWF_WB_AMOUNT = new BigDecimal("3.00");
    private static final BigDecimal DEFAULT_EPS_BASE_RATE = new BigDecimal("8.33");
    private static final BigDecimal DEFAULT_EPS_WAGE_CEILING = new BigDecimal("15000.00");
    private static final BigDecimal DEFAULT_EPS_HIGHER_EXTRA_RATE = new BigDecimal("1.16");
    private static final BigDecimal DEFAULT_NPS_EMPLOYER_RATE = new BigDecimal("10.00");
    private static final String NPS_ACTIVE_STATUS = "ACTIVE";
    private static final String LWF_STATE_CODE = "WB";
    private static final List<Integer> LWF_MONTHS = List.of(6, 12);

    /**
     * payroll_statutory_heads (V66) rows this class posts employer-side amounts against, via
     * payroll_monthly_statutory_items - a table that already existed in the schema (created
     * alongside payroll_monthly_head_items in V69) but had no JPA mapping or writer anywhere until
     * now. The catalog's own labels drove this mapping (see resolveEmployerContributions() for the
     * exact formulas): 1 "CPF" always mirrors the employee's own Head 27 CPF deduction
     * (computeCpf()) unconditionally - no eligibility gate exists anywhere on Employee for CPF
     * itself, unlike isEpsEligible()/isNpsEligible(). 4 "Pension Fund" is the EPS carve-out out of
     * that same CPF, gated on isEpsEligible() (0 otherwise). 3 "JCPF" (V78 addition, the JCI Trust's
     * own scheme) is unconditional too - whatever of stat head 1 isn't diverted to stat head 4 (i.e.
     * stat head 1 minus stat head 4), so a non-EPS-eligible employee's entire CPF still lands there
     * rather than being dropped. 15 "Employer's Contribution to National Pension Scheme" is the NPS
     * employer match. There is no other code anywhere establishing this mapping - it is this class's
     * own interpretation of an until-now-unused catalog, not a confirmed pre-existing convention.
     *
     * <p>Before the V78 JCPF addition, nothing ever posted to stat head 3, so
     * CpfLedgerSyncService.syncForBatch()'s erCredit (which reads exactly this stat head) was always
     * zero - see that class's own javadoc, which already assumed this head would eventually be wired.
     */
    private static final int STAT_HEAD_EMPLOYER_JCPF = 3;
    private static final int STAT_HEAD_EMPLOYER_EPF = 1;
    private static final int STAT_HEAD_EMPLOYER_PENSION = 4;
    private static final int STAT_HEAD_EMPLOYER_NPS = 15;

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_DA_CDA = 7;
    private static final int HEAD_DA_IDA = 8;
    private static final int HEAD_HRA = 9;
    private static final int HEAD_TRANSPORT = 10;
    private static final int HEAD_ENCASHMENT = 20;
    /** Real payroll_salary_heads catalog value (V66 seed) - NOT head 15, which is Shift Allowance (SHIFTG_ALLOW), unrelated to CEA. */
    private static final int HEAD_CEA = 22;
    private static final int HEAD_REMOTE_AREA = 21;
    private static final int HEAD_CPF = 27;
    private static final int HEAD_TDS = 40;
    private static final int HEAD_LWF = 43;
    private static final int HEAD_CAR_USE = 44;
    private static final int HEAD_GIS = 45;
    private static final int HEAD_PTAX = 49;
    private static final int HEAD_NPS = 62;
    private static final int HEAD_ACCOM_LICENSE_FEE = 63;
    private static final int HEAD_ACCOM_WATER = 64;
    private static final int HEAD_ACCOM_ELECTRIC = 65;
    private static final int HEAD_SUBSIST_ALLOW = 66;
    private static final int HEAD_DEP_ALLOW = 67;
    private static final int HEAD_CPFLOAN_PRIN = 30;
    private static final int HEAD_CPFLOAN_INT = 31;
    private static final int HEAD_JCIECCS_THRIFT = 47;
    private static final int HEAD_JCIECCS_TERM_PRIN = 52;
    private static final int HEAD_JCIECCS_TERM_INT = 53;
    private static final int HEAD_JCIECCS_EMCY_PRIN = 54;
    private static final int HEAD_JCIECCS_EMCY_INT = 55;

    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    private final PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository;
    private final PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository;
    private final EmployeeRepository employeeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final PayrollHraRateRepository payrollHraRateRepository;
    private final TransportAllowanceRateRepository transportAllowanceRateRepository;
    private final PtaxSlabRepository ptaxSlabRepository;
    private final StateMasterRepository stateMasterRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    private final EmployeeNpsDeclarationRepository npsDeclarationRepository;
    private final EmployeeVehicleAllotmentRepository vehicleAllotmentRepository;
    private final EmployeeQuarterAllotmentService quarterAllotmentService;
    private final LeaveEncashmentApplicationRepository encashmentRepository;
    private final PayrollMovementInputRepository payrollMovementInputRepository;
    private final PayrollTdsEngine payrollTdsEngine;
    private final PayrollTaxOverrideRepository payrollTaxOverrideRepository;
    private final EmployeeCeaClaimRepository ceaClaimRepository;
    private final EmployeeSuspensionRecordRepository suspensionRecordRepository;
    private final EmployeeSuspensionNecRepository suspensionNecRepository;
    private final EmployeeDeputationRecordRepository deputationRecordRepository;
    private final PayrollQueryService payrollQueryService;
    private final CpfLoanPayrollRecoveryResolverService cpfLoanPayrollRecoveryResolverService;
    private final JciEccsPayrollRecoveryResolverService jciEccsPayrollRecoveryResolverService;

    public PayrollBatchComputationService(PayrollBatchRepository payrollBatchRepository,
                                           PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                           PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository,
                                           PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository,
                                           EmployeeRepository employeeRepository,
                                           RegularPayFixationRepository regularPayFixationRepository,
                                           DaRateHistoryRepository daRateHistoryRepository,
                                           DailyAttendanceRepository dailyAttendanceRepository,
                                           PayrollHraRateRepository payrollHraRateRepository,
                                           TransportAllowanceRateRepository transportAllowanceRateRepository,
                                           PtaxSlabRepository ptaxSlabRepository,
                                           StateMasterRepository stateMasterRepository,
                                           PayrollStatutoryParameterRepository payrollStatutoryParameterRepository,
                                           EmployeeNpsDeclarationRepository npsDeclarationRepository,
                                           EmployeeVehicleAllotmentRepository vehicleAllotmentRepository,
                                           EmployeeQuarterAllotmentService quarterAllotmentService,
                                           LeaveEncashmentApplicationRepository encashmentRepository,
                                           PayrollMovementInputRepository payrollMovementInputRepository,
                                           PayrollTdsEngine payrollTdsEngine,
                                           PayrollTaxOverrideRepository payrollTaxOverrideRepository,
                                           EmployeeCeaClaimRepository ceaClaimRepository,
                                           EmployeeSuspensionRecordRepository suspensionRecordRepository,
                                           EmployeeSuspensionNecRepository suspensionNecRepository,
                                           EmployeeDeputationRecordRepository deputationRecordRepository,
                                           PayrollQueryService payrollQueryService,
                                           CpfLoanPayrollRecoveryResolverService cpfLoanPayrollRecoveryResolverService,
                                           JciEccsPayrollRecoveryResolverService jciEccsPayrollRecoveryResolverService) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.payrollMonthlyHeadItemRepository = payrollMonthlyHeadItemRepository;
        this.payrollMonthlyStatutoryItemRepository = payrollMonthlyStatutoryItemRepository;
        this.employeeRepository = employeeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.payrollHraRateRepository = payrollHraRateRepository;
        this.transportAllowanceRateRepository = transportAllowanceRateRepository;
        this.ptaxSlabRepository = ptaxSlabRepository;
        this.stateMasterRepository = stateMasterRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
        this.npsDeclarationRepository = npsDeclarationRepository;
        this.vehicleAllotmentRepository = vehicleAllotmentRepository;
        this.quarterAllotmentService = quarterAllotmentService;
        this.encashmentRepository = encashmentRepository;
        this.payrollMovementInputRepository = payrollMovementInputRepository;
        this.payrollTdsEngine = payrollTdsEngine;
        this.payrollTaxOverrideRepository = payrollTaxOverrideRepository;
        this.ceaClaimRepository = ceaClaimRepository;
        this.suspensionRecordRepository = suspensionRecordRepository;
        this.suspensionNecRepository = suspensionNecRepository;
        this.deputationRecordRepository = deputationRecordRepository;
        this.payrollQueryService = payrollQueryService;
        this.cpfLoanPayrollRecoveryResolverService = cpfLoanPayrollRecoveryResolverService;
        this.jciEccsPayrollRecoveryResolverService = jciEccsPayrollRecoveryResolverService;
    }

    /** An inclusive [start, end] span of calendar dates. */
    private record DateRange(LocalDate start, LocalDate end) {
        long days() {
            return start.isAfter(end) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
        }

        Optional<DateRange> intersect(DateRange other) {
            LocalDate s = start.isAfter(other.start) ? start : other.start;
            LocalDate e = end.isBefore(other.end) ? end : other.end;
            return s.isAfter(e) ? Optional.empty() : Optional.of(new DateRange(s, e));
        }
    }

    /**
     * One office-occupancy window within the month - payable=false marks a transit-LWP gap (no office,
     * no pay). transit=true marks Joining Time specifically: paid, and still counted at the releasing
     * office's terms for HRA/Remote-Area purposes (FR/SR convention - see class javadoc), but NOT a
     * real posting anywhere, so Transport Allowance (which compensates for a commute to a fixed office)
     * does not apply for it.
     */
    private record OfficeWindow(DateRange range, RegionalOffice office, boolean payable, boolean transit) {
    }

    /** One (office window x RegularPayFixation) cross-segment actually paid for - the unit Basic/HRA/Remote-Area are each summed over; DA is aggregated separately across all slices sharing a scale type, and Transport is skipped for transit slices - see compute(). */
    private record EarningsSlice(DateRange range, BigDecimal days, RegularPayFixation fixation, RegionalOffice office, boolean transit) {
    }

    /** hasMovement tells buildEarningsSlices() whether the office windows came from a real PayrollMovementInput (whose own day-split is authoritative) or are just the single whole-month fallback window (where ordinary attendance-based LOP still applies). */
    private record OfficeWindowResult(List<OfficeWindow> windows, boolean hasMovement) {
    }

    /** Employer-side statutory contributions for one employee-month - see resolveEmployerContributions(). Never added to grossAmount/totalDeductions; posted separately via persistEmployerContributions(), not payroll_monthly_head_items. */
    private record EmployerContributions(BigDecimal epf, BigDecimal pension, BigDecimal nps, BigDecimal jcpf) {
        private static final EmployerContributions ZERO = new EmployerContributions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** One employee's fully-computed heads for one batch, before persistence. */
    private record EmployeeComputation(
            BigDecimal lopDays,
            BigDecimal earnedBasic,
            Map<Integer, BigDecimal> daByHead,
            BigDecimal daAmount,
            BigDecimal hraAmount,
            BigDecimal transportAmount,
            BigDecimal remoteAreaAmount,
            BigDecimal encashmentAmount,
            BigDecimal ceaReimbursementAmount,
            BigDecimal deputationAllowanceAmount,
            BigDecimal cpfAmount,
            BigDecimal ptaxAmount,
            BigDecimal npsAmount,
            BigDecimal carUseRecovery,
            BigDecimal gisAmount,
            BigDecimal lwfAmount,
            BigDecimal accomLicenseFee,
            BigDecimal accomWaterCharges,
            BigDecimal accomElectricCharges,
            BigDecimal tdsAmount,
            BigDecimal cpfLoanPrincipalRecovery,
            BigDecimal cpfLoanInterestRecovery,
            BigDecimal jciEccsThrift,
            BigDecimal jciEccsTermPrincipal,
            BigDecimal jciEccsTermInterest,
            BigDecimal jciEccsEmergencyPrincipal,
            BigDecimal jciEccsEmergencyInterest,
            BigDecimal grossAmount,
            BigDecimal totalDeductions,
            BigDecimal netAmount,
            RegionalOffice representativeOffice,
            ScaleType representativeScaleType,
            List<LeaveEncashmentApplication> encashmentApplications,
            EmployerContributions employerContributions) {
    }

    @Transactional
    public PayrollBatch processBatch(Long batchId) {
        PayrollBatch batch = findBatchOrThrow(batchId);
        if (batch.getStatus() != PayrollBatchStatus.DRAFT && batch.getStatus() != PayrollBatchStatus.CALCULATED) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batchId + " must be DRAFT or CALCULATED to (re)compute but is " + batch.getStatus());
        }

        payrollMonthlyRecordRepository.deleteByBatch_Id(batchId);
        for (LeaveEncashmentApplication released : encashmentRepository.findByPayrollBatch_IdAndPayrollProcessedFalse(batchId)) {
            released.setPayrollBatch(null);
        }

        LocalDate periodStart = LocalDate.of(batch.getSalYear(), batch.getSalMonth(), 1);
        LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
        int daysInMonth = periodEnd.getDayOfMonth();
        Instant financeApprovalCutoff = LocalDateTime.of(batch.getSalYear(), batch.getSalMonth(), 25, 23, 59, 59)
                .atZone(IST).toInstant();

        // Only IN_SERVICE_EL encashments flow through the regular monthly batch - SUPERANNUATION and
        // SEPARATION are terminal-settlement encashments, disbursed once at actual separation via
        // TerminalSettlementService, not bundled into an ordinary payslip while the employee is still
        // ACTIVE. findEligibleForPayrollBatch() itself doesn't filter by encashmentType (it's shared,
        // in principle, by whatever eventually consumes non-in-service types), so that exclusion is
        // applied here instead - kept in Java rather than the JPQL query for the same testability
        // reason as the 25th-of-month cutoff below.
        Map<Long, List<LeaveEncashmentApplication>> encashmentsByEmployeeId =
                encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, batchId).stream()
                        .filter(a -> a.getEncashmentType() == EncashmentType.IN_SERVICE_EL)
                        .filter(a -> a.getFinanceApprovedAt() != null && !a.getFinanceApprovedAt().isAfter(financeApprovalCutoff))
                        .collect(Collectors.groupingBy(a -> a.getEmployee().getId()));

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int processedCount = 0;

        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.ACTIVE)) {
            OfficeWindowResult officeWindowResult = buildOfficeWindows(employee, batch.getSalMonth(), batch.getSalYear(),
                    periodStart, periodEnd, daysInMonth);

            List<EarningsSlice> slices = buildEarningsSlices(employee, officeWindowResult.windows(), officeWindowResult.hasMovement(), periodEnd);
            if (slices.isEmpty()) {
                log.warn("Skipping employee {} from payroll batch {} - no current pay fixation", employee.getId(), batchId);
                continue;
            }

            List<LeaveEncashmentApplication> encashments = encashmentsByEmployeeId.getOrDefault(employee.getId(), List.of());
            List<EmployeeCeaClaim> ceaClaims = ceaClaimRepository.findPendingPayrollDisbursement(employee.getId());
            EmployeeComputation computation = compute(employee, batch, slices, daysInMonth, encashments, ceaClaims);

            PayrollMonthlyRecord record = persistRecord(batch, employee, periodEnd, daysInMonth, computation);
            persistEmployerContributions(record, computation.employerContributions());
            for (LeaveEncashmentApplication encashment : computation.encashmentApplications()) {
                encashment.setPayrollBatch(batch);
            }
            for (EmployeeCeaClaim claim : ceaClaims) {
                claim.setPayrollProcessed(true);
                claim.setPayrollBatch(batch);
                claim.setClaimStatus(CeaClaimStatus.DISBURSED);
            }

            totalGross = totalGross.add(computation.grossAmount());
            totalDeductions = totalDeductions.add(computation.totalDeductions());
            totalNet = totalNet.add(computation.netAmount());
            processedCount++;
        }

        // Suspended employees are excluded from findByStatus(ACTIVE) above (their Employee.status is
        // SUSPENDED - see SuspensionLifecycleService.initiateSuspension()), so they need their own pass
        // through a dedicated, much smaller computation - see processSuspendedEmployees()'s own javadoc.
        SuspendedBatchTotals suspendedTotals = processSuspendedEmployees(batch, periodEnd, daysInMonth);
        totalGross = totalGross.add(suspendedTotals.gross());
        totalDeductions = totalDeductions.add(suspendedTotals.deductions());
        totalNet = totalNet.add(suspendedTotals.net());
        processedCount += suspendedTotals.count();

        batch.setTotalEmployees(processedCount);
        batch.setTotalGross(totalGross);
        batch.setTotalDeductions(totalDeductions);
        batch.setTotalNet(totalNet);
        batch.setStatus(PayrollBatchStatus.CALCULATED);
        return batch;
    }

    private record SuspendedBatchTotals(int count, BigDecimal gross, BigDecimal deductions, BigDecimal net) {
    }

    /**
     * Suspension Hook: a suspended employee draws Subsistence Allowance (Head 66), never Basic/DA (Head
     * 1/8) or CPF/NPS (Head 27/62) - CCS(CCA) Rule 10 treats suspension as "not on duty", so none of the
     * normal earnings/statutory computation in compute() applies at all; this is a deliberately separate,
     * much smaller pipeline rather than a branch bolted into compute(). Gated on that month's NEC
     * (Non-Employment Certificate) being verified - payroll_monthly_records has no free-text "status"
     * column to hold a literal 'HOLD_PENDING_NEC' value (the spec's own assumption), so is_salary_held
     * (a real boolean column) is the mechanism used instead: true and zero head items when the NEC is
     * missing/unverified, matching "payroll blocked" until HR records it.
     */
    private SuspendedBatchTotals processSuspendedEmployees(PayrollBatch batch, LocalDate periodEnd, int daysInMonth) {
        int count = 0;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;

        for (Employee employee : employeeRepository.findByStatus(EmployeeStatus.SUSPENDED)) {
            EmployeeSuspensionRecord suspension = suspensionRecordRepository
                    .findByEmployee_IdAndStatus(employee.getId(), SuspensionStatus.UNDER_SUSPENSION)
                    .orElse(null);
            if (suspension == null) {
                log.warn("Employee {} has status SUSPENDED but no active suspension record - skipping from payroll batch {}",
                        employee.getId(), batch.getId());
                continue;
            }

            PayrollMonthlyRecord record = new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                    batch.getSalMonth(), batch.getSalYear(), null,
                    employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                    CityClass.Z.name(), "IDA", daysInMonth);
            record.setDaysPresent(BigDecimal.valueOf(daysInMonth));
            record.setBasicPay(BigDecimal.ZERO);

            boolean necVerified = suspensionNecRepository
                    .findBySuspension_IdAndSalMonthAndSalYear(suspension.getId(), batch.getSalMonth(), batch.getSalYear())
                    .map(EmployeeSuspensionNec::isVerified)
                    .orElse(false);

            BigDecimal subsistenceAllowance = BigDecimal.ZERO;
            BigDecimal accomLicenseFee = BigDecimal.ZERO;
            BigDecimal tdsAmount = BigDecimal.ZERO;
            if (!necVerified) {
                record.setSalaryHeld(true);
            } else {
                Optional<RegularPayFixation> fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId());
                if (fixation.isEmpty()) {
                    log.warn("Suspended employee {} has no current pay fixation - cannot compute subsistence allowance for batch {}",
                            employee.getId(), batch.getId());
                } else {
                    ScaleType scaleType = fixation.get().getGradeScale().getScaleType();
                    BigDecimal daPercentage = resolveDaPercentage(scaleType, periodEnd);
                    BigDecimal basicPlusDa = round(fixation.get().getBasicPay()
                            .add(fixation.get().getBasicPay().multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP)));
                    subsistenceAllowance = round(basicPlusDa
                            .multiply(suspension.getCurrentSubsistencePercentage()).divide(HUNDRED, 10, RoundingMode.HALF_UP));

                    // Statutory recoveries remain allowed during suspension (TDS, accommodation license
                    // fee) - CPF/NPS (Head 27/62) are strictly never computed here, matching the hook's
                    // own requirement; car-use recovery/GIS/LWF/water-electric are out of scope for this
                    // pass given the accommodation/vehicle subsystems are deeply coupled to the normal
                    // EarningsSlice pipeline this method deliberately doesn't use.
                    Optional<EmployeeQuarterAllotment> occupancy = quarterAllotmentService.findActiveOccupancy(employee.getId(), periodEnd.withDayOfMonth(1), periodEnd);
                    if (occupancy.isPresent()) {
                        accomLicenseFee = round(occupancy.get().getLicenseFee());
                    }
                    PayrollTdsEngine.TdsResult tds = payrollTdsEngine.computeMonthlyTds(employee, batch.getFinancialYear(),
                            batch.getSalMonth(), batch.getSalYear(), subsistenceAllowance, subsistenceAllowance, basicPlusDa,
                            employee.isNpsEligible() && hasPran(employee));
                    tdsAmount = round(tds.finalAmount());
                }
            }

            BigDecimal recordGross = subsistenceAllowance;
            BigDecimal recordDeductions = accomLicenseFee.add(tdsAmount);
            record.setGrossAmount(recordGross);
            record.setTotalDeductions(recordDeductions);
            record.setNetAmount(recordGross.subtract(recordDeductions));
            record = payrollMonthlyRecordRepository.saveAndFlush(record);
            addHeadItem(record, HEAD_SUBSIST_ALLOW, subsistenceAllowance);
            addHeadItem(record, HEAD_ACCOM_LICENSE_FEE, accomLicenseFee);
            addHeadItem(record, HEAD_TDS, tdsAmount);

            gross = gross.add(recordGross);
            deductions = deductions.add(recordDeductions);
            net = net.add(recordGross.subtract(recordDeductions));
            count++;
        }

        return new SuspendedBatchTotals(count, gross, deductions, net);
    }

    /**
     * Splits the month into office-occupancy windows from this employee's PayrollMovementInput row
     * (if any) for (salMonth, salYear) - releasing office days, then Joining Time (paid, at the
     * releasing office's terms), then transit-LWP (unpaid), then receiving office days, in that
     * calendar order. Falls back to a single whole-month window at the employee's current
     * RegionalOffice when there is no movement this month. Any day-count shortfall against
     * daysInMonth (e.g. a movement whose release/join fell in different payroll months - see
     * PayrollMovementIntegrationService's own javadoc) is covered by one more window at the
     * employee's current office, rather than silently dropping those days' pay.
     */
    private OfficeWindowResult buildOfficeWindows(Employee employee, int salMonth, int salYear,
                                                   LocalDate periodStart, LocalDate periodEnd, int daysInMonth) {
        List<PayrollMovementInput> movementInputs = payrollMovementInputRepository
                .findByEmployee_IdAndPayMonthAndPayYearAndPayrollAppliedFalse(employee.getId(), salMonth, salYear);
        if (movementInputs.isEmpty()) {
            List<OfficeWindow> windows = List.of(new OfficeWindow(new DateRange(periodStart, periodEnd), employee.getRegionalOffice(), true, false));
            return new OfficeWindowResult(windows, false);
        }

        PayrollMovementInput input = movementInputs.get(0);
        List<OfficeWindow> windows = new ArrayList<>();
        int cursor = 1;

        int releasingDays = input.getReleasingOfficeDays();
        if (releasingDays > 0 && input.getReleasingOffice() != null) {
            windows.add(new OfficeWindow(dayRange(periodStart, cursor, releasingDays), input.getReleasingOffice(), true, false));
            cursor += releasingDays;
        }

        int jtDays = input.getTransitJtDays();
        if (jtDays > 0) {
            RegionalOffice jtOffice = input.getReleasingOffice() != null ? input.getReleasingOffice() : input.getReceivingOffice();
            windows.add(new OfficeWindow(dayRange(periodStart, cursor, jtDays), jtOffice, true, true));
            cursor += jtDays;
        }

        int lwpDays = input.getTransitLwpDays();
        if (lwpDays > 0) {
            windows.add(new OfficeWindow(dayRange(periodStart, cursor, lwpDays), null, false, false));
            cursor += lwpDays;
        }

        int receivingDays = input.getReceivingOfficeDays();
        if (receivingDays > 0 && input.getReceivingOffice() != null) {
            windows.add(new OfficeWindow(dayRange(periodStart, cursor, receivingDays), input.getReceivingOffice(), true, false));
            cursor += receivingDays;
        }

        if (cursor <= daysInMonth) {
            windows.add(new OfficeWindow(new DateRange(periodStart.plusDays(cursor - 1L), periodEnd), employee.getRegionalOffice(), true, false));
        }

        return new OfficeWindowResult(windows, true);
    }

    private DateRange dayRange(LocalDate periodStart, int startDayOfMonth, int lengthDays) {
        LocalDate start = periodStart.plusDays(startDayOfMonth - 1L);
        LocalDate end = periodStart.plusDays(startDayOfMonth - 1L + lengthDays - 1L);
        return new DateRange(start, end);
    }

    /**
     * Cross-joins the office windows against this employee's full RegularPayFixation history - each
     * resulting slice is the day-range where both the office and the pay-fixation rate are constant.
     * When hasMovement is true, the movement's own day-split is authoritative for the whole month and
     * ordinary DailyAttendance-based LOP is not separately deducted (see class javadoc); otherwise each
     * slice's own LOP is computed from DailyAttendance rows falling in that slice's exact date range -
     * this is what correctly allocates attendance-based LOP to the right side of a pure mid-month
     * promotion (no transfer) too.
     */
    private List<EarningsSlice> buildEarningsSlices(Employee employee, List<OfficeWindow> officeWindows,
                                                      boolean hasMovement, LocalDate periodEnd) {
        List<RegularPayFixation> fixations = regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(employee.getId());
        List<EarningsSlice> slices = new ArrayList<>();
        for (OfficeWindow officeWindow : officeWindows) {
            if (!officeWindow.payable()) {
                continue;
            }
            for (RegularPayFixation fixation : fixations) {
                LocalDate fixationEnd = fixation.getEffectiveTo() != null ? fixation.getEffectiveTo() : periodEnd;
                DateRange fixationRange = new DateRange(fixation.getEffectiveFrom(), fixationEnd);
                Optional<DateRange> overlap = officeWindow.range().intersect(fixationRange);
                if (overlap.isEmpty()) {
                    continue;
                }
                DateRange range = overlap.get();
                BigDecimal lop = hasMovement ? BigDecimal.ZERO : computeLopDays(employee.getId(), range.start(), range.end());
                BigDecimal payableDays = BigDecimal.valueOf(range.days()).subtract(lop).max(BigDecimal.ZERO);
                if (payableDays.signum() > 0) {
                    slices.add(new EarningsSlice(range, payableDays, fixation, officeWindow.office(), officeWindow.transit()));
                }
            }
        }
        return slices;
    }

    private EmployeeComputation compute(Employee employee, PayrollBatch batch, List<EarningsSlice> slices,
                                         int daysInMonth, List<LeaveEncashmentApplication> encashments,
                                         List<EmployeeCeaClaim> ceaClaims) {
        BigDecimal totalDaysBd = BigDecimal.valueOf(daysInMonth);
        boolean hasOfficeCar = employee.isOfficeCarProvided();

        BigDecimal earnedBasicTotal = BigDecimal.ZERO;
        BigDecimal hraTotal = BigDecimal.ZERO;
        BigDecimal transportTotal = BigDecimal.ZERO;
        BigDecimal remoteAreaTotal = BigDecimal.ZERO;
        Map<ScaleType, BigDecimal> basicByScaleType = new LinkedHashMap<>();

        for (EarningsSlice slice : slices) {
            BigDecimal sliceBasic = round(slice.fixation().getBasicPay().multiply(slice.days()).divide(totalDaysBd, 10, RoundingMode.HALF_UP));
            earnedBasicTotal = earnedBasicTotal.add(sliceBasic);

            ScaleType scaleType = slice.fixation().getGradeScale().getScaleType();
            basicByScaleType.merge(scaleType, sliceBasic, BigDecimal::add);

            RegionalOffice office = slice.office();
            CityClass cityClass = office != null ? office.getCityClass() : CityClass.Z;
            boolean hraSuppressed = quarterAllotmentService.isHraSuppressed(employee.getId(), slice.range().start(), slice.range().end());
            if (!hraSuppressed) {
                hraTotal = hraTotal.add(computeHouseRentAllowance(sliceBasic, cityClass, slice.range().end()));
            }

            // Transport Allowance compensates for a commute to a fixed office - it does not apply to a
            // Joining Time slice (still physically in transit, not posted anywhere yet).
            if (!hasOfficeCar && !slice.transit()) {
                BigDecimal daPercentage = resolveDaPercentage(scaleType, slice.range().end());
                BigDecimal flatTransport = computeTransportAllowance(slice.fixation().getGradeScale(), cityClass, daPercentage, slice.range().end());
                transportTotal = transportTotal.add(round(flatTransport.multiply(slice.days()).divide(totalDaysBd, 10, RoundingMode.HALF_UP)));
            }

            if (office != null) {
                BigDecimal remoteRate = resolveRemoteAreaRate(office);
                if (remoteRate.signum() > 0) {
                    remoteAreaTotal = remoteAreaTotal.add(round(sliceBasic.multiply(remoteRate).divide(HUNDRED, 10, RoundingMode.HALF_UP)));
                }
            }
        }

        // DA is rounded once per scale type on that scale's TOTAL Earned Basic for the month, not
        // per-slice - a promotion/transfer splits Earned Basic itself into several rounded pieces
        // (each day-range genuinely earns its own rupees-and-paise), but DA is a single percentage of
        // whatever the month's Basic added up to, so it gets exactly one rounding, not one per slice.
        BigDecimal daTotal = BigDecimal.ZERO;
        Map<Integer, BigDecimal> daByHead = new LinkedHashMap<>();
        LocalDate periodEndForDa = slices.stream().map(s -> s.range().end()).max(LocalDate::compareTo).orElseThrow();
        for (Map.Entry<ScaleType, BigDecimal> entry : basicByScaleType.entrySet()) {
            ScaleType scaleType = entry.getKey();
            BigDecimal daPercentage = resolveDaPercentage(scaleType, periodEndForDa);
            BigDecimal da = round(entry.getValue().multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            daTotal = daTotal.add(da);
            daByHead.merge(scaleType == ScaleType.CDA ? HEAD_DA_CDA : HEAD_DA_IDA, da, BigDecimal::add);
        }

        BigDecimal basicPlusDa = earnedBasicTotal.add(daTotal);
        BigDecimal encashmentAmount = BigDecimal.ZERO;
        for (LeaveEncashmentApplication encashment : encashments) {
            encashmentAmount = encashmentAmount.add(resolveEncashmentAmount(encashment, basicPlusDa));
        }

        // Head 22 (CEA) - passedAmount is the accounts officer's own final sanctioned figure at
        // bill-passing time, not admissibleAmount (the earlier, provisional cap computed at
        // submission) or claimedAmount - see CeaClaimService.passBill(). Excluded from
        // regularMonthlyGross for the same reason encashment is: a one-off reimbursement of an actual
        // expense, not stable recurring income, so the TDS engine's monthly-recurring projection
        // should not treat it as such - this codebase has no CEA-specific IT exemption modelling, so
        // that exclusion is this class's own judgment call, not a confirmed tax position.
        BigDecimal ceaReimbursementAmount = BigDecimal.ZERO;
        for (EmployeeCeaClaim claim : ceaClaims) {
            ceaReimbursementAmount = ceaReimbursementAmount.add(round(claim.getPassedAmount()));
        }

        // Head 67 (DEP_ALLOW) - a regular recurring monthly earning for the deputation's whole
        // duration (unlike encashment/CEA, both one-off reimbursements), so it's included in
        // regularMonthlyGross rather than excluded from it.
        BigDecimal deputationAllowanceAmount = resolveDeputationAllowance(employee, basicPlusDa, periodEndForDa);

        BigDecimal grossAmount = earnedBasicTotal.add(daTotal).add(hraTotal).add(transportTotal).add(remoteAreaTotal)
                .add(encashmentAmount).add(ceaReimbursementAmount).add(deputationAllowanceAmount);
        BigDecimal regularMonthlyGross = grossAmount.subtract(encashmentAmount).subtract(ceaReimbursementAmount);

        // The employee's effective month-end station for statutory purposes (P-Tax, loc_code on the
        // record) - the LAST slice chronologically, e.g. the receiving office after a mid-month
        // transfer, not employee.getRegionalOffice() (which the movement lifecycle never updates - see
        // class javadoc). Falls back to that same static office when there's no movement this month,
        // since the single whole-month slice's own office already IS employee.getRegionalOffice().
        EarningsSlice latestSlice = slices.stream().max(Comparator.comparing(s -> s.range().end())).orElseThrow();
        RegionalOffice effectiveOffice = latestSlice.office();

        BigDecimal cpfAmount = computeCpf(basicPlusDa);
        BigDecimal ptaxAmount = resolvePtax(effectiveOffice, grossAmount, batch.getSalMonth());
        BigDecimal npsAmount = resolveNps(employee, batch.getFinancialYear(), basicPlusDa);
        BigDecimal carUseRecovery = resolveCarUseRecovery(employee, slices);
        BigDecimal gisAmount = resolveGis();
        Cadre cadre = latestSlice.fixation().getGradeScale().getCadre();
        BigDecimal lwfAmount = resolveLwf(cadre, effectiveOffice, batch.getSalMonth());
        EmployerContributions employerContributions = resolveEmployerContributions(employee, basicPlusDa);

        BigDecimal accomLicenseFee = BigDecimal.ZERO;
        BigDecimal accomWaterCharges = BigDecimal.ZERO;
        BigDecimal accomElectricCharges = BigDecimal.ZERO;
        DateRange fullSpan = fullSpan(slices);
        Optional<EmployeeQuarterAllotment> occupancy = quarterAllotmentService.findActiveOccupancy(employee.getId(), fullSpan.start(), fullSpan.end());
        if (occupancy.isPresent()) {
            accomLicenseFee = round(occupancy.get().getLicenseFee());
            accomWaterCharges = round(occupancy.get().getWaterCharges());
            accomElectricCharges = round(occupancy.get().getElectricCharges());
        }

        PayrollTdsEngine.TdsResult tds = payrollTdsEngine.computeMonthlyTds(employee, batch.getFinancialYear(),
                batch.getSalMonth(), batch.getSalYear(), grossAmount, regularMonthlyGross, basicPlusDa,
                employee.isNpsEligible() && hasPran(employee));
        BigDecimal tdsAmount = round(tds.finalAmount());

        // Part 13/14 - the CPF Trust loan recovery resolver runs alongside every other statutory
        // deduction head computed here; RecoveryAmounts.ZERO for the overwhelming majority of employees
        // who carry no CPF Trust loan, so this is a no-op for them.
        var loanRecovery = cpfLoanPayrollRecoveryResolverService.resolve(employee, batch);

        // JCIECCS collection snapshot resolver (spec section 3) - reads the already-LOCKED
        // jcieccs_collection_detail row for this employee/batch, never recomputing loan schedules here;
        // RecoveryAmounts.ZERO for every employee until a snapshot has actually been requested for this
        // batch (POST /api/jcieccs/payroll/batches/{payrollRunId}/snapshot).
        var jciEccsRecovery = jciEccsPayrollRecoveryResolverService.resolve(employee, batch);

        BigDecimal totalDeductions = cpfAmount.add(ptaxAmount).add(npsAmount).add(carUseRecovery).add(gisAmount).add(lwfAmount)
                .add(accomLicenseFee).add(accomWaterCharges).add(accomElectricCharges).add(tdsAmount)
                .add(loanRecovery.principal()).add(loanRecovery.interest())
                .add(jciEccsRecovery.thrift()).add(jciEccsRecovery.termPrincipal()).add(jciEccsRecovery.termInterest())
                .add(jciEccsRecovery.emergencyPrincipal()).add(jciEccsRecovery.emergencyInterest());
        BigDecimal netAmount = grossAmount.subtract(totalDeductions);

        BigDecimal lopDays = BigDecimal.valueOf(daysInMonth).subtract(slices.stream().map(EarningsSlice::days).reduce(BigDecimal.ZERO, BigDecimal::add));

        return new EmployeeComputation(lopDays, earnedBasicTotal, daByHead, daTotal, hraTotal, transportTotal, remoteAreaTotal,
                encashmentAmount, ceaReimbursementAmount, deputationAllowanceAmount, cpfAmount, ptaxAmount, npsAmount, carUseRecovery, gisAmount, lwfAmount, accomLicenseFee, accomWaterCharges, accomElectricCharges,
                tdsAmount, loanRecovery.principal(), loanRecovery.interest(),
                jciEccsRecovery.thrift(), jciEccsRecovery.termPrincipal(), jciEccsRecovery.termInterest(),
                jciEccsRecovery.emergencyPrincipal(), jciEccsRecovery.emergencyInterest(),
                grossAmount, totalDeductions, netAmount, effectiveOffice,
                latestSlice.fixation().getGradeScale().getScaleType(), encashments, employerContributions);
    }

    private DateRange fullSpan(List<EarningsSlice> slices) {
        LocalDate start = slices.stream().map(s -> s.range().start()).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = slices.stream().map(s -> s.range().end()).max(LocalDate::compareTo).orElseThrow();
        return new DateRange(start, end);
    }

    private PayrollMonthlyRecord persistRecord(PayrollBatch batch, Employee employee, LocalDate periodEnd, int daysInMonth,
                                                EmployeeComputation computation) {
        RegionalOffice regionalOffice = computation.representativeOffice();
        CityClass cityClass = regionalOffice != null ? regionalOffice.getCityClass() : CityClass.Z;
        PayrollMonthlyRecord record = new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                batch.getSalMonth(), batch.getSalYear(),
                regionalOffice != null ? regionalOffice.getCode() : null,
                employee.getDesignation() != null ? employee.getDesignation().getTitle() : null,
                cityClass.name(), computation.representativeScaleType().name(), daysInMonth);

        record.setDaysLop(computation.lopDays().setScale(1, RoundingMode.HALF_UP));
        record.setDaysPresent(BigDecimal.valueOf(daysInMonth).subtract(record.getDaysLop()));
        record.setBasicPay(computation.earnedBasic());
        record.setGrossAmount(computation.grossAmount());
        record.setTotalDeductions(computation.totalDeductions());
        record.setNetAmount(computation.netAmount());
        record.setSalaryHeld(false);
        record = payrollMonthlyRecordRepository.saveAndFlush(record);

        addHeadItem(record, HEAD_BASIC, computation.earnedBasic());
        for (Map.Entry<Integer, BigDecimal> daHead : computation.daByHead().entrySet()) {
            addHeadItem(record, daHead.getKey(), daHead.getValue());
        }
        addHeadItem(record, HEAD_HRA, computation.hraAmount());
        addHeadItem(record, HEAD_TRANSPORT, computation.transportAmount());
        addHeadItem(record, HEAD_REMOTE_AREA, computation.remoteAreaAmount());
        addHeadItem(record, HEAD_ENCASHMENT, computation.encashmentAmount());
        addHeadItem(record, HEAD_CEA, computation.ceaReimbursementAmount());
        addHeadItem(record, HEAD_DEP_ALLOW, computation.deputationAllowanceAmount());
        addHeadItem(record, HEAD_CPF, computation.cpfAmount());
        addHeadItem(record, HEAD_PTAX, computation.ptaxAmount());
        addHeadItem(record, HEAD_NPS, computation.npsAmount());
        addHeadItem(record, HEAD_CAR_USE, computation.carUseRecovery());
        addHeadItem(record, HEAD_GIS, computation.gisAmount());
        addHeadItem(record, HEAD_LWF, computation.lwfAmount());
        addHeadItem(record, HEAD_ACCOM_LICENSE_FEE, computation.accomLicenseFee());
        addHeadItem(record, HEAD_ACCOM_WATER, computation.accomWaterCharges());
        addHeadItem(record, HEAD_ACCOM_ELECTRIC, computation.accomElectricCharges());
        addHeadItem(record, HEAD_TDS, computation.tdsAmount());
        addHeadItem(record, HEAD_CPFLOAN_PRIN, computation.cpfLoanPrincipalRecovery());
        addHeadItem(record, HEAD_CPFLOAN_INT, computation.cpfLoanInterestRecovery());
        addHeadItem(record, HEAD_JCIECCS_THRIFT, computation.jciEccsThrift());
        addHeadItem(record, HEAD_JCIECCS_TERM_PRIN, computation.jciEccsTermPrincipal());
        addHeadItem(record, HEAD_JCIECCS_TERM_INT, computation.jciEccsTermInterest());
        addHeadItem(record, HEAD_JCIECCS_EMCY_PRIN, computation.jciEccsEmergencyPrincipal());
        addHeadItem(record, HEAD_JCIECCS_EMCY_INT, computation.jciEccsEmergencyInterest());
        return record;
    }

    /** Only non-zero heads are inserted - matches PayrollMonthlyHeadItem's own documented rule. */
    private void addHeadItem(PayrollMonthlyRecord record, int headCount, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            payrollMonthlyHeadItemRepository.save(new PayrollMonthlyHeadItem(record, headCount, amount));
        }
    }

    /**
     * Posts employer-side statutory contributions to payroll_monthly_statutory_items - deliberately
     * NOT payroll_monthly_head_items, since nothing here is deducted from the employee's own salary
     * and none of it belongs on their payslip. Only non-zero contributions are inserted, same rule as
     * addHeadItem().
     */
    private void persistEmployerContributions(PayrollMonthlyRecord record, EmployerContributions contributions) {
        addStatutoryItem(record, STAT_HEAD_EMPLOYER_EPF, contributions.epf());
        addStatutoryItem(record, STAT_HEAD_EMPLOYER_PENSION, contributions.pension());
        addStatutoryItem(record, STAT_HEAD_EMPLOYER_NPS, contributions.nps());
        addStatutoryItem(record, STAT_HEAD_EMPLOYER_JCPF, contributions.jcpf());
    }

    private void addStatutoryItem(PayrollMonthlyRecord record, int statHeadCount, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            payrollMonthlyStatutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, statHeadCount, amount));
        }
    }

    private BigDecimal resolveEncashmentAmount(LeaveEncashmentApplication encashment, BigDecimal basicPlusDa) {
        if (encashment.getGrossAmount() != null && encashment.getGrossAmount().signum() > 0) {
            return round(encashment.getGrossAmount());
        }
        BigDecimal daysClaimed = encashment.getElDaysClaimed().add(encashment.getHplDaysClaimed());
        return round(basicPlusDa.divide(DAYS_PER_MONTH, 10, RoundingMode.HALF_UP).multiply(daysClaimed));
    }

    private BigDecimal computeLopDays(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        List<DailyAttendance> records = dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, periodStart, periodEnd);
        BigDecimal lopDays = BigDecimal.ZERO;
        for (DailyAttendance record : records) {
            if (record.getStatus() == AttendanceStatus.ABSENT) {
                lopDays = lopDays.add(BigDecimal.ONE);
            } else if (record.getStatus() == AttendanceStatus.HALF_DAY) {
                lopDays = lopDays.add(new BigDecimal("0.5"));
            }
        }
        return lopDays;
    }

    private BigDecimal resolveDaPercentage(ScaleType scaleType, LocalDate asOfDate) {
        return daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, asOfDate)
                .map(DaRateHistory::getDaPercentage)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active DA rate configured for scale type " + scaleType + " as of " + asOfDate));
    }

    private BigDecimal computeHouseRentAllowance(BigDecimal earnedBasic, CityClass cityClass, LocalDate asOfDate) {
        BigDecimal ratePercentage = payrollHraRateRepository.findActiveOn(asOfDate).stream()
                .filter(rate -> rate.getCityClass().equals(cityClass.name()))
                .findFirst()
                .map(PayrollHraRate::getRatePercentage)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active HRA rate configured for city class " + cityClass + " as of " + asOfDate));
        return round(earnedBasic.multiply(ratePercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /** Transport slab base rate + round(base * DA% / 100, 2) - see task Step B. No rate row (e.g. Board-level grade scales) resolves to zero, not a hard failure. */
    private BigDecimal computeTransportAllowance(GradeScaleMaster gradeScale, CityClass cityClass, BigDecimal daPercentage, LocalDate asOfDate) {
        BigDecimal baseRate = transportAllowanceRateRepository
                .findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(gradeScale.getId(), cityClass.name()).stream()
                .filter(rate -> !rate.getEffectiveFrom().isAfter(asOfDate))
                .findFirst()
                .map(TransportAllowanceRate::getBaseRate)
                .orElse(BigDecimal.ZERO);
        if (baseRate.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal daOnBase = round(baseRate.multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        return baseRate.add(daOnBase);
    }

    /** Head 21 - the first payroll consumer of state_master.is_remote_area/remote_allowance_percentage (V67 seed, previously read by no computation code). */
    private BigDecimal resolveRemoteAreaRate(RegionalOffice office) {
        return stateMasterRepository.findByStateNameIgnoreCase(office.getState())
                .filter(StateMaster::isRemoteArea)
                .map(StateMaster::getRemoteAllowancePercentage)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal computeCpf(BigDecimal basicPlusDa) {
        BigDecimal rate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("CPF_EMP_RATE")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_CPF_EMPLOYEE_RATE);
        return round(basicPlusDa.multiply(rate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /**
     * effectiveOffice is the employee's month-end station (see compute()'s own javadoc on
     * effectiveOffice) - a mid-month transfer resolves P-Tax (and state_master's remote-area flag,
     * separately) off wherever the employee ends the month, not the station on file at computation time.
     */
    private BigDecimal resolvePtax(RegionalOffice effectiveOffice, BigDecimal grossAmount, int salMonth) {
        String stateCode = resolveStateCode(effectiveOffice);
        if (stateCode == null) {
            return BigDecimal.ZERO;
        }
        for (PtaxSlab slab : ptaxSlabRepository.findByStateCodeOrderBySlabMinAsc(stateCode)) {
            boolean atOrAboveMin = grossAmount.compareTo(slab.getSlabMin()) >= 0;
            boolean atOrBelowMax = slab.getSlabMax() == null || grossAmount.compareTo(slab.getSlabMax()) <= 0;
            if (atOrAboveMin && atOrBelowMax) {
                if (slab.getSpecialMonth() != null && slab.getSpecialMonth() == salMonth && slab.getSpecialMonthTax() != null) {
                    return round(slab.getSpecialMonthTax());
                }
                return round(slab.getTaxAmount());
            }
        }
        return BigDecimal.ZERO;
    }

    /** Shared by resolvePtax() and resolveLwf() - RegionalOffice has no direct state_code FK, only a free-text state name resolved via a case-insensitive state_master match. Null office or an unresolvable state name both mean "no state-specific deduction/tax". */
    private String resolveStateCode(RegionalOffice office) {
        if (office == null) {
            return null;
        }
        return stateMasterRepository.findByStateNameIgnoreCase(office.getState())
                .map(StateMaster::getStateCode)
                .orElse(null);
    }

    /** Head 45 - Group Insurance Scheme flat deduction (payroll_statutory_parameters.GIS_FLAT_AMOUNT), applied to every payroll-eligible employee regardless of cadre. */
    private BigDecimal resolveGis() {
        return round(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("GIS_FLAT_AMOUNT")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_GIS_FLAT_AMOUNT));
    }

    /**
     * Head 43 - West Bengal Labour Welfare Fund flat deduction (payroll_statutory_parameters.
     * LWF_WB_AMOUNT), matching that row's own remark exactly: STAFF cadre only, employee's effective
     * month-end station in West Bengal, and only in the two months (June, December) it is actually
     * collected - the semi-annual timing has no structured column to drive off, so LWF_MONTHS is a
     * hardcoded reading of that remark rather than admin-configurable.
     */
    private BigDecimal resolveLwf(Cadre cadre, RegionalOffice effectiveOffice, int salMonth) {
        if (cadre != Cadre.STAFF || !LWF_MONTHS.contains(salMonth)) {
            return BigDecimal.ZERO;
        }
        if (!LWF_STATE_CODE.equals(resolveStateCode(effectiveOffice))) {
            return BigDecimal.ZERO;
        }
        return round(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("LWF_WB_AMOUNT")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_LWF_WB_AMOUNT));
    }

    /**
 * Employer-side CPF/EPS/NPS contributions - posted via persistEmployerContributions() to
 * payroll_monthly_statutory_items, never to the employee's own payslip (see that method's
 * javadoc). Ported from PayrollComputationService.computeEpfEps()'s real-EPFO-law formula (that
 * class's own javadoc: "these rates/ceiling are real EPFO statutory law, not JCI policy"), with
 * two changes: rates are sourced from payroll_statutory_parameters (falling back to the same
 * hardcoded values that engine uses) instead of being hardcoded outright, and - the actual gap
 * this method closes - eligibility is now checked at all:
 * <ul>
 *   <li>CPF (stat head 1): unconditional - always equals computeCpf(Basic+DA), the same 12%
 *   (CPF_EMP_RATE) figure mirrored onto the employee's own Head 27 CPF deduction. Never gated on
 *   isEpsEligible() - every employee's CPF lands on stat head 1 regardless of EPS membership.</li>
 *   <li>Pension (EPS, stat head 4): 0 unless isEpsEligible():
 *     <ul>
 *       <li><b>Standard EPS:</b> Below EPS_WAGE_CEILING (₹15,000), EPS_BASE_RATE (8.33%) of
 *       Basic+DA is carved out as pension.</li>
 *       <li><b>Higher Pension (isEpsHigherPensionEligible() & Basic+DA > ₹15,000):</b> As per the
 *       SC EPS-95 ruling and MoLE notification (3 May 2023), EPS receives 8.33% up to the ceiling
 *       plus 9.49% (8.33% + 1.16% EPS_HIGHER_EXTRA_RATE) on the wages exceeding ₹15,000. This is a
 *       REDISTRIBUTION within the employee's fixed 12% CPF, not an extra outgo on top of it.</li>
 *     </ul>
 *   </li>
 *   <li>JCPF (stat head 3): unconditional - always CPF minus Pension (stat head 1 - stat head 4),
 *   computed after pension's higher-pension bump so that redistribution is reflected here too. For
 *   a non-EPS-eligible employee (pension = 0) the employee's entire CPF lands on JCPF - this is the
 *   JCI Trust's own scheme absorbing the whole contribution when EPFO's EPS/EPF doesn't apply, so it
 *   must never be silently dropped to zero alongside pension.</li>
 *   <li>NPS employer match (stat head 15): gated the same way as the Sec 80CCD(2) TDS exemption
 *   estimate - isNpsEligible() AND hasPran() - rather than requiring an EmployeeNpsDeclaration,
 *   since the employer's contribution rate is a fixed statutory rate, not tied to whatever
 *   percentage the employee themselves elected to declare.</li>
 * </ul>
 * This CPF/EPS/NPS-employer stat-head mapping is this class's own reading of the
 * payroll_statutory_heads catalog labels (see the STAT_HEAD_* constants' own javadoc) - verify
 * against actual HR/Finance policy before relying on this for statutory compliance filing.
 */

    private EmployerContributions resolveEmployerContributions(Employee employee, BigDecimal basicPlusDa) {
        BigDecimal pension = BigDecimal.ZERO;
        if (employee.isEpsEligible()) {
            BigDecimal epsBaseRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_BASE_RATE")
                    .map(PayrollStatutoryParameter::getParamValue)
                    .orElse(DEFAULT_EPS_BASE_RATE);
            BigDecimal epsWageCeiling = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_WAGE_CEILING")
                    .map(PayrollStatutoryParameter::getParamValue)
                    .orElse(DEFAULT_EPS_WAGE_CEILING);

            BigDecimal epsWageBase = basicPlusDa.min(epsWageCeiling);
            pension = round(epsWageBase.multiply(epsBaseRate).divide(HUNDRED, 10, RoundingMode.HALF_UP));

            if (employee.isEpsHigherPensionEligible() && basicPlusDa.compareTo(epsWageCeiling) > 0) {
                BigDecimal epsHigherExtraRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("EPS_HIGHER_EXTRA_RATE")
                        .map(PayrollStatutoryParameter::getParamValue)
                        .orElse(DEFAULT_EPS_HIGHER_EXTRA_RATE);
                BigDecimal excessWages = basicPlusDa.subtract(epsWageCeiling);
                pension = pension.add(floorToInteger(excessWages.multiply(epsHigherExtraRate).divide(HUNDRED, 10, RoundingMode.HALF_UP)));
            }
        }

        BigDecimal nps = BigDecimal.ZERO;
        if (employee.isNpsEligible() && hasPran(employee)) {
            BigDecimal npsEmployerRate = payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("NPS_EMPLOYER_RATE")
                    .map(PayrollStatutoryParameter::getParamValue)
                    .orElse(DEFAULT_NPS_EMPLOYER_RATE);
            nps = round(basicPlusDa.multiply(npsEmployerRate).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        }

        // CPF (stat head 1) - unconditional, mirrors the employee's own Head 27 CPF deduction exactly
        // (same rate source, same base), never gated on isEpsEligible().
        BigDecimal epf = computeCpf(basicPlusDa);

        // JCPF (stat head 3) - whatever of the employee's CPF isn't diverted to pension. Unconditional
        // (computed here, outside the isEpsEligible() branch above) so a non-EPS-eligible employee's
        // full CPF still lands on JCPF instead of being dropped - see this method's own javadoc.
        BigDecimal jcpf = epf.subtract(pension);

        if (epf.signum() == 0 && pension.signum() == 0 && nps.signum() == 0 && jcpf.signum() == 0) {
            return EmployerContributions.ZERO;
        }
        return new EmployerContributions(epf, pension, nps, jcpf);
    }

    /**
     * NPS is mandatory once declared, but no employee deduction (Head 62) - nor, at the call site in
     * compute(), the Sec 80CCD(2) employer-contribution exemption estimate in PayrollTdsEngine - may
     * fire until the employee's PRAN (employees.pran_number) is actually on file. A declaration can
     * legitimately exist before the PRAN is allotted/recorded (the NPS Declaration Desk workflow and
     * PRAN allotment are separate processes with no ordering dependency enforced between them), so this
     * is a payroll-time gate, not a declaration-time validation.
     */
    private BigDecimal resolveNps(Employee employee, String financialYear, BigDecimal basicPlusDa) {
        if (!hasPran(employee)) {
            return BigDecimal.ZERO;
        }
        return npsDeclarationRepository.findByEmployee_IdAndFinancialYear(employee.getId(), financialYear)
                .filter(declaration -> NPS_ACTIVE_STATUS.equals(declaration.getStatus()))
                .map(EmployeeNpsDeclaration::getNpsPercentage)
                .map(percentage -> round(basicPlusDa.multiply(percentage).divide(HUNDRED, 10, RoundingMode.HALF_UP)))
                .orElse(BigDecimal.ZERO);
    }

    /** See resolveNps()'s own javadoc - shared with the Sec 80CCD(2) employer-exemption gate at its call site in compute(). */
    private boolean hasPran(Employee employee) {
        String pran = employee.getPranNumber();
        return pran != null && !pran.isBlank();
    }

    /**
     * Head 67 (DEP_ALLOW) - only for a DEPUTATION_OUT record (a JCI employee posted elsewhere while
     * remaining on JCI's own payroll - see EmployeeDeputationRecord's own javadoc) whose payOption is
     * PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE; a FOREIGN_POST_PAY_SCALE deputationist draws the borrowing
     * organization's own pay scale entirely and gets nothing from this method (rate/cap are 0.00 on
     * such records anyway - see DeputationLifecycleService.initiateDeputation()).
     */
    private BigDecimal resolveDeputationAllowance(Employee employee, BigDecimal basicPlusDa, LocalDate asOfDate) {
        return deputationRecordRepository.findByEmployee_IdAndStatus(employee.getId(), DeputationStatus.ACTIVE)
                .filter(d -> d.getDeputationDirection() == in.gov.jci.hrms.entity.DeputationDirection.DEPUTATION_OUT)
                .filter(d -> d.getPayOption() == PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE)
                .filter(d -> !d.getPeriodFrom().isAfter(asOfDate) && !d.getPeriodTo().isBefore(asOfDate))
                .map(d -> round(basicPlusDa.multiply(d.getDeputationAllowanceRate()).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                        .min(d.getDeputationAllowanceCap())))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal resolveCarUseRecovery(Employee employee, List<EarningsSlice> slices) {
        if (!employee.isOfficeCarProvided()) {
            return BigDecimal.ZERO;
        }
        DateRange span = fullSpan(slices);
        return vehicleAllotmentRepository.findByEmployee_IdAndStatus(employee.getId(), VehicleAllotmentStatus.ACTIVE).stream()
                .filter(allotment -> !allotment.getAllottedFrom().isAfter(span.end())
                        && (allotment.getSurrenderedOn() == null || !allotment.getSurrenderedOn().isBefore(span.start())))
                .filter(EmployeeVehicleAllotment::isDeductionApplicable)
                .findFirst()
                .map(allotment -> round(allotment.getMonthlyDeductionAmount() != null ? allotment.getMonthlyDeductionAmount() : defaultCarUseDeduction()))
                .orElse(BigDecimal.ZERO);
    }

    /** Falls back to payroll_statutory_parameters.DIRECTOR_CAR_USE_DEDUCTION (admin-configurable) rather than a Java constant, so an updated console value is actually picked up here - only reached when the allotment itself carries no per-allotment monthlyDeductionAmount override. */
    private BigDecimal defaultCarUseDeduction() {
        return payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("DIRECTOR_CAR_USE_DEDUCTION")
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(DEFAULT_CAR_USE_DEDUCTION);
    }

    @Transactional
    public PayrollBatch finalizeBatch(Long batchId, Long finalizedByEmployeeId) {
        PayrollBatch batch = findBatchOrThrow(batchId);
        if (batch.getStatus() != PayrollBatchStatus.CALCULATED) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batchId + " must be CALCULATED to finalize but is " + batch.getStatus());
        }
        if (payrollMonthlyRecordRepository.findByBatch_Id(batchId).isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batchId + " has no computed records - run calculate first");
        }

        for (LeaveEncashmentApplication encashment : encashmentRepository.findByPayrollBatch_Id(batchId)) {
            encashment.setPayrollProcessed(true);
        }
        for (PayrollMovementInput input : payrollMovementInputRepository
                .findByPayYearAndPayMonthOrderByCreatedAtAsc(batch.getSalYear(), batch.getSalMonth())) {
            input.setPayrollApplied(true);
        }

        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);
        if (finalizedByEmployeeId != null) {
            employeeRepository.findById(finalizedByEmployeeId).ifPresent(batch::setHrFinalizedBy);
        }
        batch.setHrFinalizedAt(Instant.now());
        return batch;
    }

    public Page<PayrollMonthlyRecordResponse> listRecords(Long batchId, Pageable pageable) {
        findBatchOrThrow(batchId);
        return payrollMonthlyRecordRepository.findByBatch_Id(batchId, pageable).map(this::toResponse);
    }

    private PayrollMonthlyRecordResponse toResponse(PayrollMonthlyRecord record) {
        List<PayrollMonthlyHeadItemResponse> items = payrollMonthlyHeadItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .map(PayrollMonthlyHeadItemResponse::from)
                .toList();
        BigDecimal encashmentAmount = amountForHead(items, HEAD_ENCASHMENT);
        BigDecimal tdsAmount = amountForHead(items, HEAD_TDS);
        boolean tdsOverridden = payrollTaxOverrideRepository
                .findByEmployee_IdAndPayrollYearAndPayrollMonth(record.getEmployee().getId(), record.getYear(), record.getMonth())
                .isPresent();

        return new PayrollMonthlyRecordResponse(record.getTranId(), record.getEmpCode(), record.getEmployee().getFullName(),
                record.getMonth(), record.getYear(), record.getBasicPay(), record.getGrossAmount(), record.getTotalDeductions(),
                record.getNetAmount(), record.isSalaryHeld(), encashmentAmount, tdsAmount, tdsOverridden, items,
                payrollQueryService.fullHeadLines(record.getTranId()));
    }

    private BigDecimal amountForHead(List<PayrollMonthlyHeadItemResponse> items, int headCount) {
        return items.stream()
                .filter(item -> item.headCount() == headCount)
                .map(PayrollMonthlyHeadItemResponse::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private PayrollBatch findBatchOrThrow(Long batchId) {
        return payrollBatchRepository.findById(batchId)
                .orElseThrow(() -> new MasterDataNotFoundException("Payroll Batch", batchId));
    }

    /**
     * Every salary-head and statutory-head amount this class produces is rounded to the nearest whole
     * rupee (HALF_UP) - not the nearest paisa. The one exception is the EPS higher-pension extra
     * contribution (see resolveEmployerContributions()), which floors instead - see floorToInteger().
     * Applying this at each component's own point of computation (rather than only where head items
     * are persisted) is deliberate: it keeps grossAmount/totalDeductions/netAmount, which are sums of
     * these same components, exactly consistent with the sum of the individual head/statutory items
     * actually posted - both are built from the same already-rounded whole-rupee pieces.
     */
    private BigDecimal round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    /** See round()'s own javadoc - used only for the EPS higher-pension extra contribution. */
    private BigDecimal floorToInteger(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }
}
