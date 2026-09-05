package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeShiftSchedule;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeShiftScheduleRepository;
import in.gov.jci.hrms.repository.ShiftMasterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves which shift_master row (and whether the day is a weekly off)
 * applies to one employee on one date, used by AttendanceAggregationService
 * to derive on-time/late/early-departure thresholds and full/half-day
 * credit per JCI Circular JCI/HO/Pers/2024-25/53 (HO/RO, 5-day week) and
 * the DPC 6-day work-week.
 *
 * Resolution order:
 * 1. employee_shift_schedule - an explicit per-employee/day roster
 *    override, if one exists (a row with shift == null is a deliberate
 *    WEEKLY_OFF override, e.g. a compensatory off).
 * 2. Otherwise, the employee's posted office type decides the default:
 *    - HEAD_OFFICE / REGIONAL_OFFICE (5-day week): Mon-Fri -> 'HO-RO',
 *      Sat/Sun -> weekly off.
 *    - DPC (6-day week): Mon-Fri -> 'DPC_WD', Sat -> 'DPC_SAT',
 *      Sun -> weekly off.
 *    An employee with neither a DPC nor a Regional Office posting (an
 *    incompletely-onboarded record) defaults to the HO/RO 5-day treatment,
 *    matching EmployeeResponse.resolveOfficeType's own "HEAD_OFFICE is
 *    itself an RO row" convention.
 *
 * ShiftDetails.shift() is never null, even on a weekly-off day: it's the
 * shift whose thresholds should evaluate any punches the employee actually
 * made that day (working a weekly off is allowed, just unusual) - only
 * ShiftDetails.weeklyOff() governs the "no punches at all -> mark WEEKOFF"
 * short-circuit in evaluateCalendar.
 */
@Service
@Transactional(readOnly = true)
public class ShiftResolutionService {

    public static final String HO_RO_SHIFT_CODE = "HO-RO";
    public static final String DPC_WEEKDAY_SHIFT_CODE = "DPC_WD";
    public static final String DPC_SATURDAY_SHIFT_CODE = "DPC_SAT";

    public record ShiftDetails(boolean weeklyOff, ShiftMaster shift) {
        public static ShiftDetails workingDay(ShiftMaster shift) {
            return new ShiftDetails(false, shift);
        }

        public static ShiftDetails weeklyOff(ShiftMaster referenceShift) {
            return new ShiftDetails(true, referenceShift);
        }
    }

    private enum OfficeCategory {
        HEAD_OFFICE, REGIONAL_OFFICE, DPC
    }

    private final EmployeeShiftScheduleRepository employeeShiftScheduleRepository;
    private final ShiftMasterRepository shiftMasterRepository;
    private final EmployeeRepository employeeRepository;

    public ShiftResolutionService(EmployeeShiftScheduleRepository employeeShiftScheduleRepository,
                                   ShiftMasterRepository shiftMasterRepository, EmployeeRepository employeeRepository) {
        this.employeeShiftScheduleRepository = employeeShiftScheduleRepository;
        this.shiftMasterRepository = shiftMasterRepository;
        this.employeeRepository = employeeRepository;
    }

    public ShiftDetails resolveShiftForEmployee(Long employeeId, LocalDate date) {
        Optional<EmployeeShiftSchedule> explicit = employeeShiftScheduleRepository.findByEmployeeIdAndScheduleDate(employeeId, date);
        if (explicit.isPresent()) {
            ShiftMaster assigned = explicit.get().getShift();
            if (assigned != null) {
                return ShiftDetails.workingDay(assigned);
            }
            // Explicit WEEKLY_OFF override - still needs a reference shift for the
            // "worked anyway" fallback, so resolve the default as normal.
            return defaultResolution(explicit.get().getEmployee(), date, true);
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return defaultResolution(employee, date, false);
    }

    private ShiftDetails defaultResolution(Employee employee, LocalDate date, boolean forceWeeklyOff) {
        OfficeCategory category = resolveOfficeCategory(employee);
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        if (category == OfficeCategory.DPC) {
            if (forceWeeklyOff || dayOfWeek == DayOfWeek.SUNDAY) {
                return ShiftDetails.weeklyOff(requireShift(DPC_WEEKDAY_SHIFT_CODE));
            }
            String code = dayOfWeek == DayOfWeek.SATURDAY ? DPC_SATURDAY_SHIFT_CODE : DPC_WEEKDAY_SHIFT_CODE;
            return ShiftDetails.workingDay(requireShift(code));
        }

        // HEAD_OFFICE / REGIONAL_OFFICE: 5-day week.
        if (forceWeeklyOff || dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return ShiftDetails.weeklyOff(requireShift(HO_RO_SHIFT_CODE));
        }
        return ShiftDetails.workingDay(requireShift(HO_RO_SHIFT_CODE));
    }

    /**
     * DPC/SUB_DPC when posted to a DPC; HEAD_OFFICE only when posted to an
     * RO row whose own officeType says so (HEAD_OFFICE is itself an RO row,
     * not a separate absence-of-RO state); REGIONAL_OFFICE for a WAREHOUSE
     * RO row or no RO/DPC posting at all - mirrors
     * EmployeeResponse.resolveOfficeType's derivation.
     */
    private OfficeCategory resolveOfficeCategory(Employee employee) {
        if (employee.getDepartmentalPurchaseCentre() != null) {
            return OfficeCategory.DPC;
        }
        RegionalOffice regionalOffice = employee.getRegionalOffice();
        if (regionalOffice != null && regionalOffice.getOfficeType() == OfficeType.HEAD_OFFICE) {
            return OfficeCategory.HEAD_OFFICE;
        }
        return OfficeCategory.REGIONAL_OFFICE;
    }

    private ShiftMaster requireShift(String shiftCode) {
        return shiftMasterRepository.findByShiftCode(shiftCode)
                .orElseThrow(() -> new BusinessRuleViolationException("Shift not configured: " + shiftCode));
    }
}
