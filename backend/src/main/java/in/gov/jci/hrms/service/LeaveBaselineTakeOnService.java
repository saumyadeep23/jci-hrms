package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.BaselineTakeOnRequest;
import in.gov.jci.hrms.dto.BaselineTakeOnResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveBaselineInitialization;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBaselineInitializationRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * The one-time HR opening-balance verification (PIMS ALMS Phase 2, Section 1):
 * validates the employee is REGULAR (EmployeeEmploymentCategory, not
 * Employee.status - see the Phase 2 research note on why those are distinct
 * concepts), that the EL encashable/enjoyable split is internally consistent
 * (both non-negative, summing to the opening balance, encashable within the
 * 300-day statutory encashment cap - NOT required to be an even 50:50 split;
 * real service-book carry-forwards are frequently uneven, e.g. 8/115), and
 * the leave type's own career/accumulation cap, then writes the baseline
 * record, seeds leave_entitlement_balance, and records an immutable
 * BASELINE_TAKEON ledger entry - all in one transaction so the three can
 * never drift out of sync.
 */
@Service
@Transactional(readOnly = true)
public class LeaveBaselineTakeOnService {

    private static final BigDecimal SPLIT_EPSILON = new BigDecimal("0.01");
    private static final BigDecimal MAX_ENCASHABLE_EL = new BigDecimal("300");

    private final LeaveBaselineInitializationRepository baselineRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeEmploymentCategoryRepository employmentCategoryRepository;

    public LeaveBaselineTakeOnService(LeaveBaselineInitializationRepository baselineRepository,
                                       LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                                       LeaveLedgerEntryRepository leaveLedgerEntryRepository,
                                       EmployeeRepository employeeRepository,
                                       LeaveTypeRepository leaveTypeRepository,
                                       EmployeeEmploymentCategoryRepository employmentCategoryRepository) {
        this.baselineRepository = baselineRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employmentCategoryRepository = employmentCategoryRepository;
    }

    @Transactional
    public BaselineTakeOnResponse takeOn(BaselineTakeOnRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
        LeaveType leaveType = leaveTypeRepository.findById(request.leaveTypeId())
                .orElseThrow(() -> new MasterDataNotFoundException("Leave Type", request.leaveTypeId()));

        requireRegularEmployee(employee);

        boolean isEl = "EL".equals(leaveType.getCode());
        BigDecimal encashable = isEl && request.openingEncashableEl() != null ? request.openingEncashableEl() : BigDecimal.ZERO;
        BigDecimal enjoyable = isEl && request.openingEnjoyableEl() != null ? request.openingEnjoyableEl() : BigDecimal.ZERO;

        if (isEl) {
            validateElSplit(request.openingBalance(), encashable, enjoyable);
        }
        validateAccumulationCap(leaveType, request.openingBalance());

        if (baselineRepository.findByEmployeeIdAndLeaveTypeIdAndAsOnDate(
                employee.getId(), leaveType.getId(), request.asOnDate()).isPresent()) {
            throw new MasterDataConflictException(
                    "A baseline already exists for employee " + employee.getId() + ", leave type " + leaveType.getCode()
                            + ", as-on date " + request.asOnDate());
        }
        if (entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(
                employee.getId(), leaveType.getId(), request.asOnDate().getYear()).isPresent()) {
            throw new MasterDataConflictException(
                    "An entitlement balance already exists for employee " + employee.getId() + ", leave type "
                            + leaveType.getCode() + ", year " + request.asOnDate().getYear());
        }

        LeaveBaselineInitialization baseline = new LeaveBaselineInitialization(
                employee, leaveType, request.asOnDate(), request.openingBalance(), encashable, enjoyable);
        baseline.setPhysicalServiceBookFolio(request.physicalServiceBookFolio());
        baseline.setVerificationOrderRef(request.verificationOrderRef());
        LeaveBaselineInitialization saved = baselineRepository.saveAndFlush(baseline);

        seedEntitlementBalance(employee, leaveType, request.asOnDate().getYear(), request.openingBalance(), encashable, enjoyable);
        writeTakeOnLedgerEntry(employee, leaveType, request.asOnDate(), request.openingBalance());

        return BaselineTakeOnResponse.from(saved);
    }

    private void requireRegularEmployee(Employee employee) {
        boolean regular = employmentCategoryRepository.findByEmployeeId(employee.getId())
                .map(category -> category.getEmploymentCategory() == EmploymentCategory.REGULAR)
                .orElse(false);
        if (!regular) {
            throw new BusinessRuleViolationException(
                    "Baseline take-on is only permitted for REGULAR employees (employee " + employee.getId() + ")");
        }
    }

    private void validateElSplit(BigDecimal openingBalance, BigDecimal encashable, BigDecimal enjoyable) {
        if (encashable.signum() < 0 || enjoyable.signum() < 0) {
            throw new BusinessRuleViolationException(
                    "opening_encashable_el and opening_enjoyable_el must both be non-negative - got "
                            + encashable + "/" + enjoyable);
        }
        if (encashable.add(enjoyable).subtract(openingBalance).abs().compareTo(SPLIT_EPSILON) > 0) {
            throw new BusinessRuleViolationException(
                    "opening_encashable_el + opening_enjoyable_el (" + encashable.add(enjoyable)
                            + ") must equal opening_balance (" + openingBalance + ") for EL");
        }
        if (encashable.compareTo(MAX_ENCASHABLE_EL) > 0) {
            throw new BusinessRuleViolationException(
                    "opening_encashable_el (" + encashable + ") exceeds the " + MAX_ENCASHABLE_EL + "-day statutory EL encashment cap");
        }
    }

    private void validateAccumulationCap(LeaveType leaveType, BigDecimal openingBalance) {
        Integer cap = leaveType.getMaxAccumulationDays();
        if (cap != null && openingBalance.compareTo(BigDecimal.valueOf(cap)) > 0) {
            throw new BusinessRuleViolationException(
                    "Opening balance " + openingBalance + " exceeds " + leaveType.getCode()
                            + "'s " + cap + "-day accumulation cap");
        }
    }

    private void seedEntitlementBalance(Employee employee, LeaveType leaveType, int year, BigDecimal openingBalance,
                                         BigDecimal encashable, BigDecimal enjoyable) {
        LeaveEntitlementBalance balance = new LeaveEntitlementBalance(employee, leaveType, year);
        balance.setOpeningBalance(openingBalance);
        balance.setCurrentBalance(openingBalance);
        balance.setAvailableBalance(openingBalance);
        balance.setEncashableOpening(encashable);
        balance.setEncashableCurrent(encashable);
        balance.setEncashableAvailable(encashable);
        balance.setEnjoyableOpening(enjoyable);
        balance.setEnjoyableCurrent(enjoyable);
        balance.setEnjoyableAvailable(enjoyable);
        entitlementBalanceRepository.saveAndFlush(balance);
    }

    private void writeTakeOnLedgerEntry(Employee employee, LeaveType leaveType, java.time.LocalDate asOnDate, BigDecimal openingBalance) {
        LeaveLedgerEntry entry = new LeaveLedgerEntry(employee, leaveType, asOnDate, openingBalance,
                "Baseline take-on: opening balance of " + openingBalance + " " + leaveType.getCode()
                        + " verified against the physical service book as on " + asOnDate,
                LeaveLedgerSource.BASELINE_TAKEON);
        leaveLedgerEntryRepository.saveAndFlush(entry);
    }
}
