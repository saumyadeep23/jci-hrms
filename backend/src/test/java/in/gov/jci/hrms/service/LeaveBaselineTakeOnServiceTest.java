package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.BaselineTakeOnRequest;
import in.gov.jci.hrms.dto.BaselineTakeOnResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBaselineInitializationRepository;
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
class LeaveBaselineTakeOnServiceTest {

    @Mock
    private LeaveBaselineInitializationRepository baselineRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;

    private LeaveBaselineTakeOnService service;
    private Employee employee;
    private LeaveType elType;

    @BeforeEach
    void setUp() {
        service = new LeaveBaselineTakeOnService(baselineRepository, entitlementBalanceRepository, leaveLedgerEntryRepository,
                employeeRepository, leaveTypeRepository, employmentCategoryRepository);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);
        elType.setMaxAccumulationDays(300);

        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(leaveTypeRepository.findById(2L)).thenReturn(Optional.of(elType));
        lenient().when(baselineRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private BaselineTakeOnRequest validElRequest() {
        return new BaselineTakeOnRequest(1L, 2L, LocalDate.of(2026, 1, 1), new BigDecimal("100.00"),
                new BigDecimal("50.00"), new BigDecimal("50.00"), "Folio-1", "Order-1");
    }

    @Test
    void takeOn_forRegularEmployeeWithValidElSplit_succeeds() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));

        BaselineTakeOnResponse response = service.takeOn(validElRequest());

        assertThat(response.openingBalance()).isEqualByComparingTo("100.00");
        assertThat(response.openingEncashableEl()).isEqualByComparingTo("50.00");
        assertThat(response.openingEnjoyableEl()).isEqualByComparingTo("50.00");
    }

    @Test
    void takeOn_forNonRegularEmployee_throws() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.CONTRACTUAL);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.takeOn(validElRequest()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("REGULAR");
    }

    @Test
    void takeOn_withUnequalButValidElSplit_succeeds() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));

        BaselineTakeOnRequest request = new BaselineTakeOnRequest(1L, 2L, LocalDate.of(2026, 1, 1),
                new BigDecimal("123.00"), new BigDecimal("8.00"), new BigDecimal("115.00"), "Folio-1", "Order-1");

        BaselineTakeOnResponse response = service.takeOn(request);

        assertThat(response.openingEncashableEl()).isEqualByComparingTo("8.00");
        assertThat(response.openingEnjoyableEl()).isEqualByComparingTo("115.00");
    }

    @Test
    void takeOn_withElSplitNotSummingToOpeningBalance_throws() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));

        BaselineTakeOnRequest request = new BaselineTakeOnRequest(1L, 2L, LocalDate.of(2026, 1, 1),
                new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("30.00"), null, null);

        assertThatThrownBy(() -> service.takeOn(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("opening_balance");
    }

    @Test
    void takeOn_withEncashableExceedingStatutoryCap_throws() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));
        elType.setMaxAccumulationDays(null);

        BaselineTakeOnRequest request = new BaselineTakeOnRequest(1L, 2L, LocalDate.of(2026, 1, 1),
                new BigDecimal("305.00"), new BigDecimal("305.00"), BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> service.takeOn(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("statutory EL encashment cap");
    }

    @Test
    void takeOn_exceedingAccumulationCap_throws() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        when(employmentCategoryRepository.findByEmployeeId(1L)).thenReturn(Optional.of(category));

        BaselineTakeOnRequest request = new BaselineTakeOnRequest(1L, 2L, LocalDate.of(2026, 1, 1),
                new BigDecimal("310.00"), new BigDecimal("155.00"), new BigDecimal("155.00"), null, null);

        assertThatThrownBy(() -> service.takeOn(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cap");
    }
}
