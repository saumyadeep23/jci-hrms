package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EncashmentGateDecisionRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveEncashmentServiceTest {

    @Mock
    private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private EmployeeServiceBookRepository serviceBookRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    private LeaveEncashmentService service;
    private Employee employee;
    private LeaveType elType;
    private LeaveEntitlementBalance balance;

    @BeforeEach
    void setUp() {
        service = new LeaveEncashmentService(encashmentRepository, entitlementBalanceRepository, leaveLedgerEntryRepository,
                serviceBookRepository, employeeRepository, leaveTypeRepository);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);

        balance = new LeaveEntitlementBalance(employee, elType, LocalDate.now().getYear());
        balance.setEncashableAvailable(new BigDecimal("20.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));

        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        lenient().when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(any(), any(), any()))
                .thenReturn(Optional.of(balance));
        lenient().when(entitlementBalanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(encashmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(serviceBookRepository.saveAndFlush(any())).thenAnswer(inv -> {
            var entry = inv.getArgument(0, in.gov.jci.hrms.entity.EmployeeServiceBook.class);
            ReflectionTestUtils.setField(entry, "id", 900L);
            return entry;
        });
    }

    @Test
    void apply_belowFifteenDaysForInServiceEl_throws() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("10.00"), null);
        assertThatThrownBy(() -> service.apply(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void apply_exceedingEncashableAvailable_throwsInsufficientBalance() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("25.00"), null);
        assertThatThrownBy(() -> service.apply(request)).isInstanceOf(InsufficientLeaveBalanceException.class);
    }

    @Test
    void apply_valid_reservesEncashableDays() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("15.00"), null);

        service.apply(request);

        assertThat(balance.getEncashableReserved()).isEqualByComparingTo("15.00");
        assertThat(balance.getEncashableAvailable()).isEqualByComparingTo("5.00");
    }

    @Test
    void financeApprove_beforeHrApproval_throws() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.financeApprove(5L, new EncashmentGateDecisionRequest(true, null), 99L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("HR");
    }

    @Test
    void financeApprove_afterHrApproval_debitsAndMarksPayrollEligibleAndWritesServiceBook() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        LeaveEncashmentResponse response = service.financeApprove(5L, new EncashmentGateDecisionRequest(true, "Approved"), 99L);

        assertThat(response.financeApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.payrollEligible()).isTrue();
        assertThat(response.serviceBookEntryId()).isEqualTo(900L);
        assertThat(balance.getEncashableCurrent()).isEqualByComparingTo("5.00");
        assertThat(balance.getEncashableEncashed()).isEqualByComparingTo("15.00");
    }

    @Test
    void hrReject_releasesReservation() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableAvailable(new BigDecimal("5.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        service.hrApprove(5L, new EncashmentGateDecisionRequest(false, "Not eligible"), 99L);

        assertThat(balance.getEncashableReserved()).isEqualByComparingTo("0.00");
        assertThat(balance.getEncashableAvailable()).isEqualByComparingTo("20.00");
    }
}
