package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveBalanceResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveBalanceServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    private LeaveBalanceService service;
    private Employee employee;
    private LeaveType leaveType;

    @BeforeEach
    void setUp() {
        service = new LeaveBalanceService(leaveBalanceRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        leaveType = new LeaveType("CL", "Casual Leave", BigDecimal.valueOf(8), false, true);
        ReflectionTestUtils.setField(leaveType, "id", 10L);
    }

    @Test
    void listForEmployee_mapsBalancesWithComputedAvailableDays() {
        LeaveBalance balance = new LeaveBalance(employee, leaveType, 2026, BigDecimal.valueOf(8));
        balance.setUsedDays(BigDecimal.valueOf(2));
        balance.setReservedDays(BigDecimal.valueOf(1));
        ReflectionTestUtils.setField(balance, "id", 100L);
        when(leaveBalanceRepository.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026)).thenReturn(List.of(balance));

        List<LeaveBalanceResponse> result = service.listForEmployee(EMPLOYEE_ID, 2026);

        assertThat(result).hasSize(1);
        LeaveBalanceResponse response = result.get(0);
        assertThat(response.leaveTypeCode()).isEqualTo("CL");
        assertThat(response.creditedDays()).isEqualByComparingTo("8");
        assertThat(response.usedDays()).isEqualByComparingTo("2");
        assertThat(response.reservedDays()).isEqualByComparingTo("1");
        assertThat(response.availableDays()).isEqualByComparingTo("5");
    }

    @Test
    void listForEmployee_whenNoneProvisioned_returnsEmptyList() {
        when(leaveBalanceRepository.findByEmployeeIdAndYear(EMPLOYEE_ID, 2026)).thenReturn(List.of());

        assertThat(service.listForEmployee(EMPLOYEE_ID, 2026)).isEmpty();
    }
}
