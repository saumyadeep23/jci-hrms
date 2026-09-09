package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeIdaArrearBreakup;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.PredecessorPayrollUnfinalizedException;
import in.gov.jci.hrms.repository.EmployeeIdaArrearBreakupRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * JIT (at-drawal-time) realization of IDA/CDA DA-order arrears - the auditable counterpart to
 * IdaProjectionEngineService's simulation. Persists employee_ida_arrear_breakups rows and posts the
 * Earning/Deduction/Statutory head amounts into the drawal month's own payroll_monthly_head_items /
 * payroll_monthly_statutory_items, keyed off the drawal month's PayrollBatch (not PayrollRun - see
 * EmployeeIdaArrearBreakup's own javadoc for why both payroll subsystems are involved here).
 */
@Service
@Transactional(readOnly = true)
public class IdaArrearComputationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final BigDecimal DEFAULT_CPF_EMP_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_CPF_EMPR_RATE = new BigDecimal("12.00");
    private static final BigDecimal DEFAULT_NPS_EMP_RATE = new BigDecimal("10.00");
    private static final BigDecimal DEFAULT_NPS_EMPLOYER_RATE = new BigDecimal("10.00");

    private static final int HEAD_ARR_DA = 14;
    private static final int HEAD_ARR_CPF = 29;
    private static final int HEAD_ARR_NPS = 62;
    private static final int STAT_HEAD_ARR_CPF = 11;
    private static final int STAT_HEAD_ARR_JCPF = 12;
    private static final int STAT_HEAD_ARR_E_NPS = 14;
    private static final int STAT_HEAD_ARR_J_NPS = 15;

    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    private final PayrollMonthlyHeadItemRepository headItemRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    private final EmployeeIdaArrearBreakupRepository arrearBreakupRepository;
    private final LeaveEncashmentApplicationRepository encashmentRepository;
    private final PayrollComputationService payrollComputationService;

    public IdaArrearComputationService(PayrollBatchRepository payrollBatchRepository,
                                        PayrollRunRepository payrollRunRepository,
                                        PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                        PayrollMonthlyHeadItemRepository headItemRepository,
                                        PayrollMonthlyStatutoryItemRepository statutoryItemRepository,
                                        PayrollStatutoryParameterRepository payrollStatutoryParameterRepository,
                                        EmployeeIdaArrearBreakupRepository arrearBreakupRepository,
                                        LeaveEncashmentApplicationRepository encashmentRepository,
                                        PayrollComputationService payrollComputationService) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
        this.arrearBreakupRepository = arrearBreakupRepository;
        this.encashmentRepository = encashmentRepository;
        this.payrollComputationService = payrollComputationService;
    }

    /**
     * Every retro month strictly before the drawal month must have a DISBURSED payroll_batches row -
     * the spec's own literal status list (CALCULATED/VERIFIED/LOCKED/DISBURSED) doesn't match any real
     * PayrollBatchStatus value; DISBURSED is the only one of the four that actually exists and is also
     * the only status under which payroll_monthly_records for that month is guaranteed immutable.
     */
    public void validatePredecessorGate(int drawalMonth, int drawalYear, LocalDate effectiveFrom) {
        YearMonth cursor = YearMonth.from(effectiveFrom);
        YearMonth lastRetro = YearMonth.of(drawalYear, drawalMonth).minusMonths(1);
        while (!cursor.isAfter(lastRetro)) {
            PayrollBatchStatus status = payrollBatchRepository.findBySalMonthAndSalYear(cursor.getMonthValue(), cursor.getYear())
                    .map(PayrollBatch::getStatus)
                    .orElse(null);
            if (status != PayrollBatchStatus.DISBURSED) {
                throw new PredecessorPayrollUnfinalizedException(
                        "Payroll for " + cursor + " must be DISBURSED before an IDA arrear drawal in "
                                + YearMonth.of(drawalYear, drawalMonth) + " can proceed (is " + (status != null ? status : "not yet run") + ")");
            }
            cursor = cursor.plusMonths(1);
        }
    }

    public record RealizedArrearResult(BigDecimal totalGrossArrear, BigDecimal totalNetArrear, int monthsRealized) {
    }

    @Transactional
    public RealizedArrearResult materializeRealizedArrears(Employee employee, PayrollBatch currentBatch, DaRateHistory daOrder) {
        PayrollMonthlyRecord currentRecord = payrollMonthlyRecordRepository
                .findByBatch_IdAndEmployee_Id(currentBatch.getId(), employee.getId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employee.getId() + " has no payroll_monthly_records row in drawal batch " + currentBatch.getId()));

        PayrollRun payrollRun = resolveOrCreatePayrollRun(currentBatch.getSalYear(), currentBatch.getSalMonth());
        String pensionScheme = pensionScheme(employee);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalEmployeeCpf = BigDecimal.ZERO;
        BigDecimal totalEmployerJcpf = BigDecimal.ZERO;
        BigDecimal totalEmployeeNps = BigDecimal.ZERO;
        BigDecimal totalEmployerNps = BigDecimal.ZERO;
        int monthsRealized = 0;

        YearMonth cursor = YearMonth.from(daOrder.getEffectiveFrom());
        YearMonth lastRetro = YearMonth.of(currentBatch.getSalYear(), currentBatch.getSalMonth()).minusMonths(1);
        while (!cursor.isAfter(lastRetro)) {
            YearMonth month = cursor;
            Optional<EmployeeIdaArrearBreakup> already = arrearBreakupRepository.findByEmployee_IdAndDaRateHistory_IdAndRetroMonthAndRetroYear(
                    employee.getId(), daOrder.getId(), month.getMonthValue(), month.getYear());
            EmployeeIdaArrearBreakup breakup = already.orElseGet(() -> computeAndPersistRetroMonth(employee, daOrder, payrollRun, month, pensionScheme));
            if (breakup != null) {
                totalGross = totalGross.add(breakup.getGrossIdaArrear());
                totalEmployeeCpf = totalEmployeeCpf.add(breakup.getEmployeeCpfArrear());
                totalEmployerJcpf = totalEmployerJcpf.add(breakup.getEmployerJcpfArrear());
                totalEmployeeNps = totalEmployeeNps.add(breakup.getEmployeeNpsArrear());
                totalEmployerNps = totalEmployerNps.add(breakup.getEmployerNpsArrear());
                monthsRealized++;
            }
            cursor = cursor.plusMonths(1);
        }

        addHeadItem(currentRecord, HEAD_ARR_DA, totalGross);
        if ("NPS".equals(pensionScheme)) {
            addHeadItem(currentRecord, HEAD_ARR_NPS, totalEmployeeNps);
            addStatutoryItem(currentRecord, STAT_HEAD_ARR_E_NPS, totalEmployeeNps);
            addStatutoryItem(currentRecord, STAT_HEAD_ARR_J_NPS, totalEmployerNps);
        } else {
            addHeadItem(currentRecord, HEAD_ARR_CPF, totalEmployeeCpf);
            addStatutoryItem(currentRecord, STAT_HEAD_ARR_CPF, totalEmployeeCpf);
            addStatutoryItem(currentRecord, STAT_HEAD_ARR_JCPF, totalEmployerJcpf);
        }

        BigDecimal totalNet = totalGross.subtract(totalEmployeeCpf).subtract(totalEmployeeNps);
        materializeEncashmentArrears(employee, daOrder, avgDeltaRate(daOrder));
        return new RealizedArrearResult(totalGross, totalNet, monthsRealized);
    }

    private EmployeeIdaArrearBreakup computeAndPersistRetroMonth(Employee employee, DaRateHistory daOrder, PayrollRun payrollRun,
                                                                  YearMonth month, String pensionScheme) {
        Optional<PayrollBatch> retroBatch = payrollBatchRepository.findBySalMonthAndSalYear(month.getMonthValue(), month.getYear())
                .filter(b -> b.getStatus() == PayrollBatchStatus.DISBURSED);
        if (retroBatch.isEmpty()) {
            return null;
        }
        Optional<PayrollMonthlyRecord> retroRecord = payrollMonthlyRecordRepository.findByBatch_IdAndEmployee_Id(retroBatch.get().getId(), employee.getId());
        if (retroRecord.isEmpty()) {
            return null;
        }
        PayrollMonthlyRecord record = retroRecord.get();
        BigDecimal oldRate = resolveOldRate(daOrder);
        BigDecimal newRate = daOrder.getDaPercentage();
        BigDecimal deltaRate = newRate.subtract(oldRate);

        BigDecimal gross = round(record.getBasicPay().multiply(deltaRate).divide(HUNDRED, 6, RoundingMode.HALF_UP)
                .multiply(record.getDaysPresent()).divide(BigDecimal.valueOf(record.getDaysInMonth()), 6, RoundingMode.HALF_UP));

        BigDecimal employeeCpf = BigDecimal.ZERO, employerJcpf = BigDecimal.ZERO, employeeNps = BigDecimal.ZERO, employerNps = BigDecimal.ZERO;
        LocalDate monthEnd = month.atEndOfMonth();
        if ("NPS".equals(pensionScheme)) {
            employeeNps = round(gross.multiply(rate("NPS_EMP_RATE", DEFAULT_NPS_EMP_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            employerNps = round(gross.multiply(rate("NPS_EMPLOYER_RATE", DEFAULT_NPS_EMPLOYER_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        } else {
            employeeCpf = round(gross.multiply(rate("CPF_EMP_RATE", DEFAULT_CPF_EMP_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
            employerJcpf = round(gross.multiply(rate("CPF_EMPR_RATE", DEFAULT_CPF_EMPR_RATE, monthEnd)).divide(HUNDRED, 6, RoundingMode.HALF_UP));
        }
        BigDecimal net = gross.subtract(employeeCpf).subtract(employeeNps);

        EmployeeIdaArrearBreakup breakup = new EmployeeIdaArrearBreakup(employee, daOrder, payrollRun, month.getMonthValue(), month.getYear(),
                record.getBasicPay(), record.getDaysInMonth(), record.getDaysPresent(), oldRate, newRate, deltaRate, gross);
        breakup.setEmployeeCpfArrear(employeeCpf);
        breakup.setEmployerJcpfArrear(employerJcpf);
        breakup.setEmployeeNpsArrear(employeeNps);
        breakup.setEmployerNpsArrear(employerNps);
        breakup.setNetIdaArrear(net);
        return arrearBreakupRepository.save(breakup);
    }

    /** The row this order superseded, regardless of its (by-now-flipped-to-false) isActive flag - see DaRateHistoryRepository's own javadoc for this exact method. */
    private BigDecimal resolveOldRate(DaRateHistory daOrder) {
        return payrollComputationService.resolveDaPercentage(daOrder.getScaleType(), daOrder.getEffectiveFrom().minusDays(1));
    }

    private BigDecimal avgDeltaRate(DaRateHistory daOrder) {
        return daOrder.getDaPercentage().subtract(resolveOldRate(daOrder));
    }

    /**
     * Child leave_encashment_application rows (application_type = DA_ARREAR) for every dual-approved
     * REGULAR encashment settled since the order's effectiveFrom - see
     * LeaveEncashmentApplication.daArrearChild()'s own javadoc for why these are created already
     * approved/payroll-eligible rather than re-entering the HR/Finance queue. "basic" is the employee's
     * current basic (same documented simplification as IdaProjectionEngineService.leaveEncashmentArrear()).
     */
    private void materializeEncashmentArrears(Employee employee, DaRateHistory daOrder, BigDecimal deltaRate) {
        if (deltaRate.signum() == 0) {
            return;
        }
        Instant from = daOrder.getEffectiveFrom().atStartOfDay(IST).toInstant();
        Instant to = Instant.now();
        List<LeaveEncashmentApplication> settled = encashmentRepository.findSettledInWindow(employee.getId(), from, to);
        for (LeaveEncashmentApplication parent : settled) {
            BigDecimal encashedDays = parent.getElDaysClaimed().add(parent.getHplDaysClaimed());
            BigDecimal basic = parent.getGrossAmount() != null && parent.getDaRateApplied() != null && encashedDays.signum() > 0
                    ? parent.getGrossAmount().multiply(new BigDecimal("30"))
                            .divide(encashedDays, 6, RoundingMode.HALF_UP)
                            .divide(BigDecimal.ONE.add(parent.getDaRateApplied().divide(HUNDRED, 6, RoundingMode.HALF_UP)), 6, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal arrearAmount = round(basic.multiply(deltaRate).divide(HUNDRED, 6, RoundingMode.HALF_UP)
                    .divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP)
                    .multiply(encashedDays));
            if (arrearAmount.signum() > 0) {
                encashmentRepository.save(LeaveEncashmentApplication.daArrearChild(parent, daOrder, arrearAmount));
            }
        }
    }

    private PayrollRun resolveOrCreatePayrollRun(int salYear, int salMonth) {
        return payrollRunRepository.findByCycleYearAndCycleMonth(salYear, salMonth)
                .orElseGet(() -> {
                    PayrollComputationService.CycleDates dates = payrollComputationService.deriveCycleDates(salYear, salMonth);
                    return payrollRunRepository.saveAndFlush(new PayrollRun(salYear, salMonth, dates.startDate(), dates.endDate()));
                });
    }

    private void addHeadItem(PayrollMonthlyRecord record, int headCount, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            headItemRepository.save(new PayrollMonthlyHeadItem(record, headCount, amount));
        }
    }

    private void addStatutoryItem(PayrollMonthlyRecord record, int statHeadCount, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) {
            statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, statHeadCount, amount));
        }
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

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
