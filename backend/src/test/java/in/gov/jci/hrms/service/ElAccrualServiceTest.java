package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElAccrualServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;

    private ElAccrualService service;
    private Employee employee;
    private LeaveType elType;

    @BeforeEach
    void setUp() {
        // 2027-01-01 is not a leap-year cycle date, but the accrual amount must
        // still be exactly 15.00 regardless - the invariant the task explicitly
        // calls out ("never 16 in leap year").
        Clock clock = Clock.fixed(Instant.parse("2028-01-01T00:00:00Z"), ZoneOffset.UTC);
        service = new ElAccrualService(employeeRepository, employmentCategoryRepository, leaveTypeRepository,
                entitlementBalanceRepository, leaveLedgerEntryRepository, clock);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);

        lenient().when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        lenient().when(employeeRepository.findAll()).thenReturn(List.of(employee));
        lenient().when(employmentCategoryRepository.findByEmployeeId(1L))
                .thenReturn(Optional.of(new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR)));
        lenient().when(leaveLedgerEntryRepository.findByEmployeeIdAndSourceAndEntryDateBetween(any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(entitlementBalanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void runSemiAnnualAccrualFor_2028LeapYearCycle_creditsExactly15Days_notMore() {
        when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 2L, 2028))
                .thenReturn(Optional.empty());

        service.runSemiAnnualAccrualFor(LocalDate.of(2028, 1, 1));

        var captor = org.mockito.ArgumentCaptor.forClass(LeaveEntitlementBalance.class);
        org.mockito.Mockito.verify(entitlementBalanceRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getCreditedDays()).isEqualByComparingTo("15.00");
        assertThat(captor.getValue().getEncashableCredited()).isEqualByComparingTo("7.50");
        assertThat(captor.getValue().getEnjoyableCredited()).isEqualByComparingTo("7.50");
    }

    @Test
    void runSemiAnnualAccrualFor_skipsNonRegularEmployees() {
        when(employmentCategoryRepository.findByEmployeeId(1L))
                .thenReturn(Optional.of(new EmployeeEmploymentCategory(employee, EmploymentCategory.CONTRACTUAL)));

        service.runSemiAnnualAccrualFor(LocalDate.of(2028, 1, 1));

        org.mockito.Mockito.verifyNoInteractions(entitlementBalanceRepository);
    }

    @Test
    void debitFavorablePreservation_debitsEnjoyableBeforeEncashable() {
        LeaveEntitlementBalance balance = new LeaveEntitlementBalance(employee, elType, 2028);
        balance.setEnjoyableCurrent(new BigDecimal("5.00"));
        balance.setEncashableCurrent(new BigDecimal("10.00"));
        balance.setCurrentBalance(new BigDecimal("15.00"));
        balance.setAvailableBalance(new BigDecimal("15.00"));

        BigDecimal[] split = service.debitFavorablePreservation(balance, new BigDecimal("8.00"));

        assertThat(split[0]).isEqualByComparingTo("5.00"); // fully drains enjoyable first
        assertThat(split[1]).isEqualByComparingTo("3.00"); // spills over into encashable
        assertThat(balance.getEnjoyableCurrent()).isEqualByComparingTo("0.00");
        assertThat(balance.getEncashableCurrent()).isEqualByComparingTo("7.00");
    }

    @Test
    void debitFavorablePreservation_whenInsufficientTotalBalance_throws() {
        LeaveEntitlementBalance balance = new LeaveEntitlementBalance(employee, elType, 2028);
        balance.setEnjoyableCurrent(new BigDecimal("1.00"));
        balance.setEncashableCurrent(new BigDecimal("1.00"));

        assertThatThrownBy(() -> service.debitFavorablePreservation(balance, new BigDecimal("5.00")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
