package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EncashmentGateDecisionRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.CareerEventType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

    private final LeaveEncashmentApplicationRepository encashmentRepository;
    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    private final LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    private final EmployeeServiceBookRepository serviceBookRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveEncashmentService(LeaveEncashmentApplicationRepository encashmentRepository,
                                   LeaveEntitlementBalanceRepository entitlementBalanceRepository,
                                   LeaveLedgerEntryRepository leaveLedgerEntryRepository,
                                   EmployeeServiceBookRepository serviceBookRepository,
                                   EmployeeRepository employeeRepository,
                                   LeaveTypeRepository leaveTypeRepository) {
        this.encashmentRepository = encashmentRepository;
        this.entitlementBalanceRepository = entitlementBalanceRepository;
        this.leaveLedgerEntryRepository = leaveLedgerEntryRepository;
        this.serviceBookRepository = serviceBookRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Transactional
    public LeaveEncashmentResponse apply(LeaveEncashmentRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        if (request.encashmentType() == EncashmentType.IN_SERVICE_EL) {
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
        entitlementBalanceRepository.saveAndFlush(balance);

        LeaveEncashmentApplication application = new LeaveEncashmentApplication(
                employee, request.encashmentType(), request.elDaysClaimed(), request.hplDaysClaimed());
        return LeaveEncashmentResponse.from(encashmentRepository.saveAndFlush(application));
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

        LeaveLedgerEntry entry = new LeaveLedgerEntry(application.getEmployee(), balance.getLeaveType(), LocalDate.now(),
                amount.negate(), "EL encashment debit (" + application.getEncashmentType() + ") - application " + application.getId(),
                LeaveLedgerSource.EL_ENCASHMENT_DEBIT);
        leaveLedgerEntryRepository.saveAndFlush(entry);

        EmployeeServiceBook serviceBookEntry = new EmployeeServiceBook(application.getEmployee(), LocalDate.now(),
                CareerEventType.EARNED_LEAVE_ENCASHABLE.name());
        serviceBookEntry.setRemarks(amount + " days EL encashed (" + application.getEncashmentType() + ")");
        serviceBookEntry.setMigrated(false);
        EmployeeServiceBook savedServiceBookEntry = serviceBookRepository.saveAndFlush(serviceBookEntry);

        application.setPayrollEligible(true);
        application.setServiceBookEntry(savedServiceBookEntry);
    }

    private void releaseReservation(LeaveEncashmentApplication application) {
        LeaveEntitlementBalance balance = resolveElBalance(application.getEmployee().getId(), LocalDate.now().getYear());
        balance.setEncashableReserved(balance.getEncashableReserved().subtract(application.getElDaysClaimed()));
        balance.setEncashableAvailable(balance.getEncashableAvailable().add(application.getElDaysClaimed()));
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
