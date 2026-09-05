package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CombinedLeaveApplicationRequest;
import in.gov.jci.hrms.dto.CombinedLeaveApplicationResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveSession;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.IncompatibleLeaveCombinationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CombinedLeaveApplicationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private LeaveValidationService leaveValidationService;

    private CombinedLeaveApplicationService service;
    private Employee employee;
    private LeaveType clType;
    private LeaveType rhType;
    private Holiday rhHoliday;

    @BeforeEach
    void setUp() {
        service = new CombinedLeaveApplicationService(employeeRepository, leaveTypeRepository, holidayRepository,
                leaveApplicationRepository, leaveValidationService);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        clType = new LeaveType("CL", "Casual Leave", new BigDecimal("8.0"), false, true);
        ReflectionTestUtils.setField(clType, "id", 100L);
        rhType = new LeaveType("RH", "Restricted Holiday", new BigDecimal("2.0"), false, true);
        ReflectionTestUtils.setField(rhType, "id", 101L);

        rhHoliday = new Holiday(LocalDate.of(2026, 3, 10), "Some Restricted Holiday", HolidayType.RESTRICTED);
        ReflectionTestUtils.setField(rhHoliday, "id", 500L);

        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(leaveTypeRepository.findById(100L)).thenReturn(Optional.of(clType));
        lenient().when(leaveTypeRepository.findById(101L)).thenReturn(Optional.of(rhType));
        lenient().when(holidayRepository.findById(500L)).thenReturn(Optional.of(rhHoliday));
        lenient().when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(any(), any(), any())).thenReturn(List.of());
        lenient().when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());
        lenient().when(leaveApplicationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1"));
    }

    @Test
    void create_sameDaySplitWithOppositeHalves_createsLinkedPair() {
        CombinedLeaveApplicationRequest request = new CombinedLeaveApplicationRequest(1L, 100L,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LeaveSession.FIRST_HALF,
                101L, LocalDate.of(2026, 3, 10), LeaveSession.SECOND_HALF, 500L, "Personal work");

        CombinedLeaveApplicationResponse response = service.create(request);

        assertThat(response.groupApplicationId()).isNotNull();
        assertThat(response.clApplication().groupApplicationId()).isEqualTo(response.groupApplicationId());
        assertThat(response.rhApplication().groupApplicationId()).isEqualTo(response.groupApplicationId());
    }

    @Test
    void create_sameDaySplitWithSameHalfTwice_throws() {
        CombinedLeaveApplicationRequest request = new CombinedLeaveApplicationRequest(1L, 100L,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LeaveSession.FIRST_HALF,
                101L, LocalDate.of(2026, 3, 10), LeaveSession.FIRST_HALF, 500L, "Personal work");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("opposite half-day sessions");
    }

    @Test
    void create_contiguousRhSuffix_createsLinkedPair() {
        CombinedLeaveApplicationRequest request = new CombinedLeaveApplicationRequest(1L, 100L,
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), LeaveSession.FULL_DAY,
                101L, LocalDate.of(2026, 3, 10), LeaveSession.FULL_DAY, 500L, "Extended weekend");

        CombinedLeaveApplicationResponse response = service.create(request);

        assertThat(response.rhApplication().startDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void create_rhNotContiguousOrSameDay_throws() {
        CombinedLeaveApplicationRequest request = new CombinedLeaveApplicationRequest(1L, 100L,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), LeaveSession.FULL_DAY,
                101L, LocalDate.of(2026, 3, 10), LeaveSession.FULL_DAY, 500L, "Not contiguous");

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void create_whenClContiguousWithApprovedEl_throwsIncompatibleLeaveCombination() {
        LeaveType elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 200L);
        LeaveApplication existingEl = new LeaveApplication(employee, elType, LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 8), BigDecimal.ONE, "Prior EL");
        existingEl.setStatus(LeaveApplicationStatus.APPROVED);

        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(1L, LeaveApplicationStatus.APPROVED,
                LocalDate.of(2026, 3, 8))).thenReturn(List.of(existingEl));

        CombinedLeaveApplicationRequest request = new CombinedLeaveApplicationRequest(1L, 100L,
                LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9), LeaveSession.FULL_DAY,
                101L, LocalDate.of(2026, 3, 10), LeaveSession.FULL_DAY, 500L, "Adjacent to EL");

        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IncompatibleLeaveCombinationException.class);
    }
}
