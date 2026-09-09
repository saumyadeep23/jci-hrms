package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DaProjectionBatch;
import in.gov.jci.hrms.entity.DaProjectionBatchStatus;
import in.gov.jci.hrms.entity.DaProjectionEmployee;
import in.gov.jci.hrms.entity.DaProjectionMonthlyBreakup;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DaProjectionBatchRepository;
import in.gov.jci.hrms.repository.DaProjectionEmployeeRepository;
import in.gov.jci.hrms.repository.DaProjectionMonthlyBreakupRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Simulates/projects the CPSE IDA DA-rate-revision arrear impact ahead of an actual drawal - a
 * "what-if" engine over da_projection_batches/employees/monthly_breakups (all three tables pre-exist
 * this service - see V-whatever migration, same "table before entity" situation as employee_cea_claims).
 * IdaArrearComputationService is the sibling that later JIT-materializes the REAL, auditable
 * employee_ida_arrear_breakups rows once an order is actually disbursed - this class never writes there.
 *
 * <p>For a retro month whose payroll_batches row (by sal_month/sal_year) is DISBURSED, the "exact
 * historical" basic/days figures are read straight out of payroll_monthly_records for that batch+
 * employee - the only place in this schema that genuinely holds per-employee historical basic pay
 * (payroll_runs, the older cycle-status table, has no employee-level figures at all). For the current,
 * still-open month (no DISBURSED batch yet for it), the employee's current RegularPayFixation basic is
 * used and the full month is assumed paid.
 */
@Service
@Transactional(readOnly = true)
public class IdaProjectionEngineService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final BigDecimal DEFAULT_CPF_EMP_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_CPF_EMPR_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_NPS_EMP_RATE = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_NPS_EMPLOYER_RATE = new BigDecimal("10.00");

    /** payroll_batches statuses treated as "this month is already locked/being processed" for Scenario A rollover - the spec's own CALCULATED/VERIFIED/LOCKED/DISBURSED list doesn't match any real PayrollBatchStatus value, so this collapses to the three real non-DRAFT, non-terminal-reject/cancel statuses. */
    private static final java.util.Set<PayrollBatchStatus> LOCKED_STATUSES =
            java.util.Set.of(PayrollBatchStatus.HR_FINALIZED, PayrollBatchStatus.FINANCE_APPROVED, PayrollBatchStatus.DISBURSED);

    private final DaProjectionBatchRepository batchRepository;
    private final DaProjectionEmployeeRepository projectionEmployeeRepository;
    private final DaProjectionMonthlyBreakupRepository monthlyBreakupRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    private final LeaveEncashmentApplicationRepository encashmentRepository;

    public IdaProjectionEngineService(DaProjectionBatchRepository batchRepository,
                                       DaProjectionEmployeeRepository projectionEmployeeRepository,
                                       DaProjectionMonthlyBreakupRepository monthlyBreakupRepository,
                                       DaRateHistoryRepository daRateHistoryRepository,
                                       EmployeeRepository employeeRepository,
                                       RegularPayFixationRepository regularPayFixationRepository,
                                       PayrollBatchRepository payrollBatchRepository,
                                       PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                       PayrollStatutoryParameterRepository payrollStatutoryParameterRepository,
                                       LeaveEncashmentApplicationRepository encashmentRepository) {
        this.batchRepository = batchRepository;
        this.projectionEmployeeRepository = projectionEmployeeRepository;
        this.monthlyBreakupRepository = monthlyBreakupRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.payrollBatchRepository = payrollBatchRepository;
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
        this.encashmentRepository = encashmentRepository;
    }

    @Transactional
    public DaProjectionBatch executeSimulation(ScaleType scaleType, BigDecimal newDaRate, LocalDate effectiveFrom,
                                                int drawalMonth, int drawalYear, Long createdByEmployeeId) {
        Employee createdBy = createdByEmployeeId != null ? resolveEmployee(createdByEmployeeId) : null;
        // The day BEFORE effectiveFrom, not effectiveFrom itself - a simulation may run after the new
        // DaRateHistory order row already exists (HR records the order, then simulates its impact), in
        // which case querying "as of effectiveFrom" would return the new rate itself as "old".
        BigDecimal oldDaRate = daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, effectiveFrom.minusDays(1))
                .map(DaRateHistory::getDaPercentage)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active DA rate history found for scale type " + scaleType + " as of " + effectiveFrom));

        int retroMonthsCount = countRetroMonths(effectiveFrom, drawalMonth, drawalYear);
        String projectionCode = "IDAPROJ-" + scaleType + "-" + effectiveFrom.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + System.currentTimeMillis();

        DaProjectionBatch batch = new DaProjectionBatch(projectionCode, scaleType, oldDaRate, newDaRate, effectiveFrom,
                drawalMonth, drawalYear, retroMonthsCount, createdBy);
        batch = batchRepository.saveAndFlush(batch);

        runSimulation(batch);
        return batch;
    }

    /**
     * Scenario A enforcement: if the current target drawal month's payroll_batches row is already
     * locked (HR_FINALIZED/FINANCE_APPROVED/DISBURSED) or today is past the 24th while still inside the
     * target month (the monthly finance-approval cutoff PayrollBatchComputationService itself enforces
     * at the 25th - see its own financeApprovalCutoff), that month can no longer absorb a fresh order:
     * roll the drawal out one month, widen the retro window by one month to cover the month that just
     * got skipped, and re-run the simulation so the now-locked month is read from real finalized figures
     * instead of the "current master basic, full month" open-month assumption.
     */
    @Transactional
    public DaProjectionBatch checkCutoffAndRollOver(Long batchId) {
        DaProjectionBatch batch = findBatchOrThrow(batchId);

        boolean targetLocked = payrollBatchRepository.findBySalMonthAndSalYear(batch.getExpectedDrawalMonth(), batch.getExpectedDrawalYear())
                .map(PayrollBatch::getStatus)
                .map(LOCKED_STATUSES::contains)
                .orElse(false);
        LocalDate today = LocalDate.now(IST);
        boolean pastCutoffInTargetMonth = today.getDayOfMonth() > 24
                && today.getMonthValue() == batch.getExpectedDrawalMonth()
                && today.getYear() == batch.getExpectedDrawalYear();

        if (targetLocked || pastCutoffInTargetMonth) {
            YearMonth nextDrawal = YearMonth.of(batch.getExpectedDrawalYear(), batch.getExpectedDrawalMonth()).plusMonths(1);
            batch.setExpectedDrawalMonth(nextDrawal.getMonthValue());
            batch.setExpectedDrawalYear(nextDrawal.getYear());
            batch.setRetroMonthsCount(batch.getRetroMonthsCount() + 1);

            projectionEmployeeRepository.deleteByBatch_Id(batchId);
            runSimulation(batch);
        }
        return batch;
    }

    /**
     * Runs the Scenario A rollover guard one last time, then verifies a matching da_rate_history order
     * row (same scaleType/effectiveFrom/daPercentage) already exists - this engine never creates that
     * row itself, DaRateHistoryService owns that lifecycle exclusively - before staging the batch as
     * ORDER_COMMITTED for the drawal run.
     */
    @Transactional
    public DaProjectionBatch commitOrder(Long batchId) {
        DaProjectionBatch batch = findBatchOrThrow(batchId);
        if (batch.getStatus() != DaProjectionBatchStatus.DRAFT) {
            throw new BusinessRuleViolationException("DA projection batch " + batchId + " must be DRAFT to commit but is " + batch.getStatus());
        }
        DaProjectionBatch rolledOver = checkCutoffAndRollOver(batchId);

        DaRateHistory order = daRateHistoryRepository.findByScaleTypeAndEffectiveFrom(rolledOver.getScaleType(), rolledOver.getEffectiveFrom())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No DA rate history order exists yet for " + rolledOver.getScaleType() + " effective " + rolledOver.getEffectiveFrom()
                                + " - create it via the DA Rate Manager before committing this projection"));
        if (order.getDaPercentage().compareTo(rolledOver.getNewDaRate()) != 0) {
            throw new BusinessRuleViolationException("DA rate history order (" + order.getDaPercentage()
                    + "%) does not match this projection's simulated new rate (" + rolledOver.getNewDaRate() + "%)");
        }

        rolledOver.setStatus(DaProjectionBatchStatus.ORDER_COMMITTED);
        return rolledOver;
    }

    public List<DaProjectionMonthlyBreakup> getMonthlyBreakups(Long batchId) {
        return monthlyBreakupRepository.findByBatch_IdOrderByEmployee_IdAscSalYearAscSalMonthAsc(batchId);
    }

    public DaProjectionBatch getBatch(Long batchId) {
        return findBatchOrThrow(batchId);
    }

    public List<DaProjectionEmployee> getProjectionEmployees(Long batchId) {
        return projectionEmployeeRepository.findByBatch_Id(batchId);
    }

    public List<DaProjectionMonthlyBreakup> getMonthlyBreakupsForEmployee(Long projectionEmployeeId) {
        return monthlyBreakupRepository.findByProjectionEmployee_IdOrderBySalYearAscSalMonthAsc(projectionEmployeeId);
    }

    private void runSimulation(DaProjectionBatch batch) {
        List<Employee> activeEmployees = employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        BigDecimal deltaRate = batch.getNewDaRate().subtract(batch.getOldDaRate());

        BigDecimal batchArrearGross = BigDecimal.ZERO;
        BigDecimal batchArrearNet = BigDecimal.ZERO;
        BigDecimal batchEmployerCostOutgo = BigDecimal.ZERO;
        BigDecimal batchMonthlyGrossDelta = BigDecimal.ZERO;
        BigDecimal batchMonthlyEmployerCostDelta = BigDecimal.ZERO;
        int employeesWithImpact = 0;

        for (Employee employee : activeEmployees) {
            String pensionScheme = pensionScheme(employee);
            DaProjectionEmployee projectionEmployee = new DaProjectionEmployee(batch, employee, pensionScheme);

            BigDecimal grossArrears = BigDecimal.ZERO;
            BigDecimal employeeCpfArrear = BigDecimal.ZERO;
            BigDecimal employerJcpfArrear = BigDecimal.ZERO;
            BigDecimal employeeNpsArrear = BigDecimal.ZERO;
            BigDecimal employerNpsArrear = BigDecimal.ZERO;
            int monthsCount = 0;
            List<DaProjectionMonthlyBreakup> pendingBreakups = new java.util.ArrayList<>();

            YearMonth cursor = YearMonth.from(batch.getEffectiveFrom());
            YearMonth lastRetroMonth = YearMonth.of(batch.getExpectedDrawalYear(), batch.getExpectedDrawalMonth()).minusMonths(1);
            while (!cursor.isAfter(lastRetroMonth)) {
                Optional<MonthFigures> figures = resolveMonthFigures(employee, cursor);
                if (figures.isPresent()) {
                    MonthFigures f = figures.get();
                    BigDecimal deltaDa = round(f.actualBasic().multiply(deltaRate).divide(HUNDRED, 6, RoundingMode.HALF_UP)
                            .multiply(f.paidDays()).divide(BigDecimal.valueOf(f.totalDays()), 6, RoundingMode.HALF_UP));

                    BigDecimal empCpf = BigDecimal.ZERO, emprJcpf = BigDecimal.ZERO, empNps = BigDecimal.ZERO, emprNps = BigDecimal.ZERO;
                    LocalDate monthEnd = cursor.atEndOfMonth();
                    if ("NPS".equals(pensionScheme)) {
                        empNps = round(deltaDa.multiply(rate("NPS_EMP_RATE", DEFAULT_NPS_EMP_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
                        emprNps = round(deltaDa.multiply(rate("NPS_EMPLOYER_RATE", DEFAULT_NPS_EMPLOYER_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
                    } else {
                        empCpf = round(deltaDa.multiply(rate("CPF_EMP_RATE", DEFAULT_CPF_EMP_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
                        emprJcpf = round(deltaDa.multiply(rate("CPF_EMPR_RATE", DEFAULT_CPF_EMPR_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
                    }
                    BigDecimal netMonthly = deltaDa.subtract(empCpf).subtract(empNps);
                    BigDecimal employerCostMonthly = deltaDa.add(emprJcpf).add(emprNps);

                    DaProjectionMonthlyBreakup breakup = new DaProjectionMonthlyBreakup(projectionEmployee, batch, employee,
                            cursor.getMonthValue(), cursor.getYear(), cursor.format(MONTH_LABEL_FORMAT), f.totalDays(), f.paidDays(),
                            f.actualBasic(), batch.getOldDaRate(), batch.getNewDaRate(), deltaDa);
                    breakup.setEmployeeCpfArrear(empCpf);
                    breakup.setEmployerJcpfArrear(emprJcpf);
                    breakup.setEmployeeNpsArrear(empNps);
                    breakup.setEmployerNpsArrear(emprNps);
                    breakup.setNetMonthlyArrear(netMonthly);
                    breakup.setEmployerCostMonthly(employerCostMonthly);

                    grossArrears = grossArrears.add(deltaDa);
                    employeeCpfArrear = employeeCpfArrear.add(empCpf);
                    employerJcpfArrear = employerJcpfArrear.add(emprJcpf);
                    employeeNpsArrear = employeeNpsArrear.add(empNps);
                    employerNpsArrear = employerNpsArrear.add(emprNps);
                    monthsCount++;

                    if (cursor.equals(lastRetroMonth)) {
                        // The month immediately preceding drawal is also this employee's forward run-rate once the new rate goes live permanently.
                        batchMonthlyGrossDelta = batchMonthlyGrossDelta.add(deltaDa);
                        batchMonthlyEmployerCostDelta = batchMonthlyEmployerCostDelta.add(employerCostMonthly);
                    }
                    pendingBreakups.add(breakup);
                }
                cursor = cursor.plusMonths(1);
            }

            BigDecimal encashmentArrear = leaveEncashmentArrear(employee, batch.getEffectiveFrom(), deltaRate);
            BigDecimal netArrearPayable = grossArrears.subtract(employeeCpfArrear).subtract(employeeNpsArrear).add(encashmentArrear);
            BigDecimal employerCost = grossArrears.add(employerJcpfArrear).add(employerNpsArrear);

            projectionEmployee.setTotalMonthsCount(monthsCount);
            projectionEmployee.setTotalGrossArrears(grossArrears);
            projectionEmployee.setTotalEmployeeCpfArrear(employeeCpfArrear);
            projectionEmployee.setTotalEmployerJcpfArrear(employerJcpfArrear);
            projectionEmployee.setTotalEmployeeNpsArrear(employeeNpsArrear);
            projectionEmployee.setTotalEmployerNpsArrear(employerNpsArrear);
            projectionEmployee.setTotalLeaveEncashmentArrear(encashmentArrear);
            projectionEmployee.setTotalNetArrearPayable(netArrearPayable);
            projectionEmployee.setTotalEmployerCost(employerCost);

            if (monthsCount > 0 || encashmentArrear.signum() > 0) {
                projectionEmployeeRepository.save(projectionEmployee);
                for (DaProjectionMonthlyBreakup breakup : pendingBreakups) {
                    monthlyBreakupRepository.save(breakup);
                }
                employeesWithImpact++;
                batchArrearGross = batchArrearGross.add(grossArrears);
                batchArrearNet = batchArrearNet.add(netArrearPayable);
                batchEmployerCostOutgo = batchEmployerCostOutgo.add(employerCost);
            }
        }

        batch.setTotalActiveEmployees(employeesWithImpact);
        batch.setTotalArrearGrossOutgo(batchArrearGross);
        batch.setTotalArrearNetOutgo(batchArrearNet);
        batch.setTotalEmployerCostOutgo(batchEmployerCostOutgo);
        batch.setTotalMonthlyGrossDelta(batchMonthlyGrossDelta);
        batch.setTotalMonthlyEmployerCostDelta(batchMonthlyEmployerCostDelta);
    }

    private record MonthFigures(BigDecimal actualBasic, int totalDays, BigDecimal paidDays) {
    }

    private Optional<MonthFigures> resolveMonthFigures(Employee employee, YearMonth month) {
        Optional<PayrollBatch> finalizedBatch = payrollBatchRepository.findBySalMonthAndSalYear(month.getMonthValue(), month.getYear())
                .filter(b -> b.getStatus() == PayrollBatchStatus.DISBURSED);
        if (finalizedBatch.isPresent()) {
            return payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(finalizedBatch.get().getId(), employee.getId())
                    .map(r -> new MonthFigures(r.getBasicPay(), r.getDaysInMonth(), r.getDaysPresent()));
        }
        // Open/unfinalized month (including the current one) - current master basic, full month assumed paid.
        return regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .map(RegularPayFixation::getBasicPay)
                .map(basic -> new MonthFigures(basic, month.lengthOfMonth(), BigDecimal.valueOf(month.lengthOfMonth())));
    }

    /**
     * (basic * deltaDa% / 30) * encashedDays for every dual-approved encashment settled since
     * effectiveFrom - "basic" here is the employee's CURRENT basic (RegularPayFixation), not the
     * historical basic as of the encashment's own sanction date, since a settled encashment's own
     * emoluments snapshot (LeaveEncashmentApplication.currentBasicPay-at-apply-time) isn't itself
     * basic-pay-only-recoverable from the stored gross_amount without re-deriving the DA rate that
     * produced it - a simplification worth flagging rather than silently getting exactly "right".
     */
    private BigDecimal leaveEncashmentArrear(Employee employee, LocalDate effectiveFrom, BigDecimal deltaRate) {
        Instant from = effectiveFrom.atStartOfDay(IST).toInstant();
        Instant to = Instant.now();
        List<LeaveEncashmentApplication> settled = encashmentRepository.findSettledInWindow(employee.getId(), from, to);
        if (settled.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal currentBasic = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .map(RegularPayFixation::getBasicPay)
                .orElse(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        for (LeaveEncashmentApplication app : settled) {
            BigDecimal encashedDays = app.getElDaysClaimed().add(app.getHplDaysClaimed());
            BigDecimal arrear = currentBasic.multiply(deltaRate).divide(HUNDRED, 6, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP)
                    .multiply(encashedDays);
            total = total.add(round(arrear));
        }
        return total;
    }

    private BigDecimal rate(String paramKey, BigDecimal fallback, LocalDate asOfDate) {
        return payrollStatutoryParameterRepository.findActiveParamOnDate(paramKey, asOfDate)
                .map(p -> p.getParamValue())
                .orElse(fallback);
    }

    private String pensionScheme(Employee employee) {
        boolean nps = employee.isNpsEligible() && employee.getPranNumber() != null && !employee.getPranNumber().isBlank();
        return nps ? "NPS" : "CPF";
    }

    private int countRetroMonths(LocalDate effectiveFrom, int drawalMonth, int drawalYear) {
        YearMonth start = YearMonth.from(effectiveFrom);
        YearMonth lastRetro = YearMonth.of(drawalYear, drawalMonth).minusMonths(1);
        if (lastRetro.isBefore(start)) {
            return 0;
        }
        return (int) (start.until(lastRetro, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private DaProjectionBatch findBatchOrThrow(Long id) {
        return batchRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("DA Projection Batch", id));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }
}
