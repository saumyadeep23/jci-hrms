package in.gov.jci.hrms.service;

import in.gov.jci.hrms.config.PayrollRateProperties;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuperannuationDetails;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeSuperannuationDetailsRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Phase 5 payroll calculation engine (SRS 4.4/4.4a). Two categories of
 * figures are involved here, and it matters which is which:
 *
 * <p>Statutory (real law, hardcoded exactly): EPF 12%, EPS-95 8.33%, and the
 * Rs. 15,000 EPS wage ceiling - see computeEpfEps().
 *
 * <p>JCI wage-settlement figures (NOT given anywhere in the SRS excerpt this
 * was built against, so made configurable rather than guessed): HRA % by
 * city class and the Transport Allowance base amount - see
 * {@link PayrollRateProperties}, whose current defaults are explicitly
 * documented placeholders, not confirmed JCI policy.
 *
 * <p>Known data-model limitations this service works around rather than
 * silently ignores:
 * <ul>
 *   <li>"Mid-cycle promotion/increment splitting" - there is no
 *   effective-dated history of an employee's basic pay anywhere in this
 *   schema (resolveCurrentGradeScale() resolves a live current
 *   regular_pay_fixations pointer - V60 - and GradeScaleMaster only stores
 *   a min/max band). proratedAmount() is the correct, reusable
 *   day-weighting primitive a multi-segment split would be built from, but
 *   computeBasicPay() can currently only ever produce a single segment
 *   against gradeScale.getMinimumBasic() - there's nothing to split against.
 *   <li>An employee's actual current basic pay figure isn't tracked either
 *   (only the scale's min/max band is) - this service uses the scale
 *   minimum as a stand-in, which is a simplification, not a true "current
 *   basic pay".
 *   <li>Salary hold (FR-PAY.15) is grounded in EmployeeStatus != ACTIVE,
 *   plus a superannuation-date guard (see isSalaryHeld(Employee, LocalDate))
 *   - there's no other dedicated hold-reason data source in this schema to
 *   check against.
 *   <li>City class for HRA comes from the employee's RegionalOffice; an
 *   employee with none assigned (e.g. HO-based) defaults to the lowest
 *   tier (Z) rather than blocking computation entirely - flagged here as a
 *   simplification, not a confirmed policy for HO staff.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PayrollComputationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");
    private static final BigDecimal EPF_RATE = new BigDecimal("0.12");
    private static final BigDecimal EPS_RATE = new BigDecimal("0.0833");
    private static final BigDecimal EPS_WAGE_CEILING = new BigDecimal("15000.00");

    private final DailyAttendanceRepository dailyAttendanceRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final PayrollRateProperties rateProperties;
    private final EmployeeSuperannuationDetailsRepository superannuationDetailsRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;

    public PayrollComputationService(DailyAttendanceRepository dailyAttendanceRepository,
                                      DaRateHistoryRepository daRateHistoryRepository,
                                      PayrollRateProperties rateProperties,
                                      EmployeeSuperannuationDetailsRepository superannuationDetailsRepository,
                                      RegularPayFixationRepository regularPayFixationRepository) {
        this.dailyAttendanceRepository = dailyAttendanceRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.rateProperties = rateProperties;
        this.superannuationDetailsRepository = superannuationDetailsRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
    }

    public record CycleDates(LocalDate startDate, LocalDate endDate) {
    }

    public record EpfEpsResult(BigDecimal employeeEpf, BigDecimal employerEpf, BigDecimal employerEps) {
    }

    public record PayrollComputationResult(
            BigDecimal basicPay,
            BigDecimal dearnessAllowance,
            BigDecimal houseRentAllowance,
            BigDecimal transportAllowance,
            BigDecimal employeeEpf,
            BigDecimal employerEpf,
            BigDecimal employerEps,
            BigDecimal totalEarnings,
            BigDecimal totalDeductions,
            BigDecimal employerContributions,
            BigDecimal netPay,
            BigDecimal lopDays,
            boolean hold
    ) {
    }

    /**
     * cycle_month names the run by the month its 25th falls in, e.g.
     * "August 2026" (year=2026, month=8) runs 2026-07-26 to 2026-08-25.
     */
    public CycleDates deriveCycleDates(int cycleYear, int cycleMonth) {
        LocalDate endDate = LocalDate.of(cycleYear, cycleMonth, 25);
        LocalDate startDate = endDate.minusMonths(1).withDayOfMonth(26);
        return new CycleDates(startDate, endDate);
    }

    /**
     * ABSENT days count as 1.0 LOP day, HALF_DAY as 0.5. A day with no
     * daily_attendance row at all is NOT counted as LOP - there's no batch
     * process in this system yet that populates one row per employee per
     * working day, so an absent row is the only positive evidence of a
     * loss-of-pay day this service has to go on.
     */
    public BigDecimal computeLopDays(Long employeeId, LocalDate cycleStart, LocalDate cycleEnd) {
        List<DailyAttendance> records =
                dailyAttendanceRepository.findByEmployeeIdAndAttendanceDateBetween(employeeId, cycleStart, cycleEnd);

        BigDecimal lopDays = BigDecimal.ZERO;
        for (DailyAttendance record : records) {
            if (record.getStatus() == AttendanceStatus.ABSENT) {
                lopDays = lopDays.add(BigDecimal.ONE);
            } else if (record.getStatus() == AttendanceStatus.HALF_DAY) {
                lopDays = lopDays.add(HALF_DAY);
            }
        }
        return lopDays;
    }

    /**
     * gradeScale comes from the employee's current regular_pay_fixations
     * row (V60: employee.getPayScale()/pay_scale_master is gone) - see
     * resolveCurrentGradeScale(). Still just the scale's minimum as a
     * stand-in for "current basic pay" (see class Javadoc's known
     * limitation) - the grade_scale_master switch doesn't change that.
     */
    public BigDecimal computeBasicPay(GradeScaleMaster gradeScale, LocalDate cycleStart, LocalDate cycleEnd, BigDecimal lopDays) {
        BigDecimal totalCycleDays = BigDecimal.valueOf(ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1);
        BigDecimal payableDays = totalCycleDays.subtract(lopDays);
        return proratedAmount(gradeScale.getMinimumBasic(), payableDays, totalCycleDays);
    }

    /** V60: the sole source of an employee's current grade/scale is their active regular_pay_fixations row - see RegularPayFixation.getGradeScale(). */
    public GradeScaleMaster resolveCurrentGradeScale(Employee employee) {
        return regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                .map(RegularPayFixation::getGradeScale)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Employee " + employee.getId() + " has no current pay fixation; cannot compute payroll"));
    }

    /**
     * Day-weights a full-cycle amount by payable/total days. This is the
     * primitive a mid-cycle pay-change split would sum across multiple
     * segments - see class Javadoc for why there's currently only ever one.
     */
    public BigDecimal proratedAmount(BigDecimal fullCycleAmount, BigDecimal payableDays, BigDecimal totalCycleDays) {
        return round(fullCycleAmount.multiply(payableDays).divide(totalCycleDays, 10, RoundingMode.HALF_UP));
    }

    public BigDecimal resolveDaPercentage(ScaleType scaleType, LocalDate asOfDate) {
        return daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, asOfDate)
                .map(DaRateHistory::getDaPercentage)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active DA rate configured for scale type " + scaleType + " as of " + asOfDate));
    }

    public BigDecimal computeDearnessAllowance(BigDecimal basicPay, BigDecimal daPercentage) {
        return round(basicPay.multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    /**
     * Defaults to the lowest HRA tier (Z) when the employee has no
     * RegionalOffice assigned - a simplification, not a confirmed policy
     * for HO-based staff. See class Javadoc.
     */
    public CityClass resolveCityClass(Employee employee) {
        RegionalOffice regionalOffice = employee.getRegionalOffice();
        return regionalOffice != null ? regionalOffice.getCityClass() : CityClass.Z;
    }

    public BigDecimal computeHouseRentAllowance(BigDecimal basicPay, BigDecimal dearnessAllowance, CityClass cityClass) {
        BigDecimal percent = switch (cityClass) {
            case X -> rateProperties.getHraPercentX();
            case Y -> rateProperties.getHraPercentY();
            case Z -> rateProperties.getHraPercentZ();
        };
        return round(basicPay.add(dearnessAllowance).multiply(percent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
    }

    public BigDecimal computeTransportAllowance(BigDecimal daPercentage) {
        BigDecimal multiplier = BigDecimal.ONE.add(daPercentage.divide(HUNDRED, 10, RoundingMode.HALF_UP));
        return round(rateProperties.getTransportAllowanceBase().multiply(multiplier));
    }

    /**
     * Employee EPF: 12% of (Basic+DA), uncapped. EPS-95 (employer-side
     * carve-out): 8.33% of (Basic+DA), capped at the Rs. 15,000 statutory
     * EPS wage ceiling. Employer EPF is the remainder of the employer's
     * matching 12% after the EPS carve-out. These rates/ceiling are real
     * EPFO statutory law, not JCI policy - safe to hardcode.
     */
    public EpfEpsResult computeEpfEps(BigDecimal basicPay, BigDecimal dearnessAllowance) {
        BigDecimal pfWages = basicPay.add(dearnessAllowance);
        BigDecimal employeeEpf = round(pfWages.multiply(EPF_RATE));
        BigDecimal epsWageBase = pfWages.min(EPS_WAGE_CEILING);
        BigDecimal employerEps = round(epsWageBase.multiply(EPS_RATE));
        BigDecimal employerEpfTotal = round(pfWages.multiply(EPF_RATE));
        BigDecimal employerEpf = employerEpfTotal.subtract(employerEps);
        return new EpfEpsResult(employeeEpf, employerEpf, employerEps);
    }

    /**
     * FR-PAY.15, minimally implemented: held whenever the employee isn't
     * ACTIVE. See class Javadoc for why there's nothing richer to check.
     * Status-only convenience overload - {@link #compute} uses the
     * period-aware overload below, which is the one that actually matters
     * for superannuation: SuperannuationScheduledTask's daily sweep should
     * already have flipped status away from ACTIVE by the time a payroll
     * run touches a retired employee, but this is defense-in-depth for the
     * same-day gap between a superannuation date landing mid-period and
     * that sweep running.
     */
    public boolean isSalaryHeld(Employee employee) {
        return employee.getStatus() != EmployeeStatus.ACTIVE;
    }

    public boolean isSalaryHeld(Employee employee, LocalDate payrollPeriodStartDate) {
        if (isSalaryHeld(employee)) {
            return true;
        }
        return superannuationDetailsRepository.findByEmployeeId(employee.getId())
                .map(EmployeeSuperannuationDetails::getSuperannuationDate)
                .map(date -> date.isBefore(payrollPeriodStartDate))
                .orElse(false);
    }

    public PayrollComputationResult compute(Employee employee, PayrollRun payrollRun) {
        LocalDate cycleStart = payrollRun.getStartDate();
        LocalDate cycleEnd = payrollRun.getEndDate();

        GradeScaleMaster gradeScale = resolveCurrentGradeScale(employee);
        BigDecimal lopDays = computeLopDays(employee.getId(), cycleStart, cycleEnd);
        BigDecimal basicPay = computeBasicPay(gradeScale, cycleStart, cycleEnd, lopDays);
        BigDecimal daPercentage = resolveDaPercentage(gradeScale.getScaleType(), cycleEnd);
        BigDecimal dearnessAllowance = computeDearnessAllowance(basicPay, daPercentage);
        CityClass cityClass = resolveCityClass(employee);
        BigDecimal houseRentAllowance = computeHouseRentAllowance(basicPay, dearnessAllowance, cityClass);
        BigDecimal transportAllowance = computeTransportAllowance(daPercentage);
        EpfEpsResult epfEps = computeEpfEps(basicPay, dearnessAllowance);

        BigDecimal totalEarnings = basicPay.add(dearnessAllowance).add(houseRentAllowance).add(transportAllowance);
        BigDecimal totalDeductions = epfEps.employeeEpf();
        BigDecimal employerContributions = epfEps.employerEpf().add(epfEps.employerEps());
        BigDecimal netPay = totalEarnings.subtract(totalDeductions);
        boolean hold = isSalaryHeld(employee, cycleStart);

        return new PayrollComputationResult(basicPay, dearnessAllowance, houseRentAllowance, transportAllowance,
                epfEps.employeeEpf(), epfEps.employerEpf(), epfEps.employerEps(),
                totalEarnings, totalDeductions, employerContributions, netPay, lopDays, hold);
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
