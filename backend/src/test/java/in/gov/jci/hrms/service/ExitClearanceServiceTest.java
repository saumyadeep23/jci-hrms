package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExitClearanceDepartment;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceItemRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExitClearanceServiceTest {

    @Mock
    private ExitClearanceRequestRepository clearanceRequestRepository;
    @Mock
    private ExitClearanceItemRepository clearanceItemRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeReleaseService employeeReleaseService;

    private ExitClearanceService service;
    private Employee employee;
    private static final Long EMPLOYEE_ID = 700L;
    private static final Long REQUEST_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new ExitClearanceService(clearanceRequestRepository, clearanceItemRepository, employeeRepository, employeeReleaseService);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        employee = new Employee("2801", "Test", "Employee", "test@example.com", LocalDate.of(1990, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    @Test
    void initiateExit_provisionsOneItemPerDepartment() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(clearanceRequestRepository.existsByEmployeeIdAndStatusNot(EMPLOYEE_ID, ExitClearanceStatus.CANCELLED)).thenReturn(false);
        when(clearanceRequestRepository.save(any())).thenAnswer(inv -> {
            ExitClearanceRequest r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", REQUEST_ID);
            return r;
        });

        service.initiateExit(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), "remarks");

        // Never hardcode the department count - JCIECCS (Task 4 Phase 3) added an 8th department to this
        // same enum; asserting against ExitClearanceDepartment.values().length keeps this test correct
        // regardless of how many departments the enum has.
        ArgumentCaptor<ExitClearanceItem> captor = ArgumentCaptor.forClass(ExitClearanceItem.class);
        verify(clearanceItemRepository, times(ExitClearanceDepartment.values().length)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExitClearanceItem::getDepartmentCode)
                .containsExactlyInAnyOrder(ExitClearanceDepartment.values());
    }

    @Test
    void initiateExit_employeeAlreadyHasOpenRequest_throws() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(clearanceRequestRepository.existsByEmployeeIdAndStatusNot(EMPLOYEE_ID, ExitClearanceStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> service.initiateExit(EMPLOYEE_ID, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateDepartmentClearance_partialClear_movesToInProgress() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ExitClearanceItem item = new ExitClearanceItem(request, ExitClearanceDepartment.FINANCE);
        ReflectionTestUtils.setField(item, "id", 10L);
        when(clearanceItemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(clearanceItemRepository.countByClearanceRequestIdAndStatus(REQUEST_ID, ExitClearanceItemStatus.CLEARED)).thenReturn(1L);
        when(clearanceItemRepository.countByClearanceRequestId(REQUEST_ID)).thenReturn(7L);

        service.updateDepartmentClearance(10L, ExitClearanceItemStatus.CLEARED, null, "ok", 99L);

        assertThat(request.getStatus()).isEqualTo(ExitClearanceStatus.CLEARANCE_IN_PROGRESS);
    }

    @Test
    void updateDepartmentClearance_allSevenCleared_movesToClearancesCompleted() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ExitClearanceItem item = new ExitClearanceItem(request, ExitClearanceDepartment.FINANCE);
        ReflectionTestUtils.setField(item, "id", 10L);
        when(clearanceItemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(clearanceItemRepository.countByClearanceRequestIdAndStatus(REQUEST_ID, ExitClearanceItemStatus.CLEARED)).thenReturn(7L);
        when(clearanceItemRepository.countByClearanceRequestId(REQUEST_ID)).thenReturn(7L);

        service.updateDepartmentClearance(10L, ExitClearanceItemStatus.CLEARED, null, "ok", 99L);

        assertThat(request.getStatus()).isEqualTo(ExitClearanceStatus.CLEARANCES_COMPLETED);
    }

    @Test
    void updateDepartmentClearance_oneRejectedWithDues_neverReachesCompleted() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ExitClearanceItem item = new ExitClearanceItem(request, ExitClearanceDepartment.STORES);
        ReflectionTestUtils.setField(item, "id", 11L);
        when(clearanceItemRepository.findById(11L)).thenReturn(Optional.of(item));
        // 6 CLEARED + this one REJECTED_WITH_DUES = 7 total, but clearedCount stays 6.
        when(clearanceItemRepository.countByClearanceRequestIdAndStatus(REQUEST_ID, ExitClearanceItemStatus.CLEARED)).thenReturn(6L);
        when(clearanceItemRepository.countByClearanceRequestId(REQUEST_ID)).thenReturn(7L);

        service.updateDepartmentClearance(11L, ExitClearanceItemStatus.REJECTED_WITH_DUES, new java.math.BigDecimal("500.00"), "dues owed", 99L);

        assertThat(request.getStatus()).isEqualTo(ExitClearanceStatus.CLEARANCE_IN_PROGRESS);
    }

    @Test
    void finalizeReleaseOrder_notYetClearancesCompleted_throws() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 12, 31), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", ExitClearanceStatus.CLEARANCE_IN_PROGRESS);
        when(clearanceRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.finalizeReleaseOrder(REQUEST_ID, "REL-001", LocalDate.of(2026, 12, 31)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void finalizeReleaseOrder_deceased_releasesWithDeceasedStatus() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.DECEASED, LocalDate.of(2026, 6, 1), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", ExitClearanceStatus.CLEARANCES_COMPLETED);
        when(clearanceRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ExitClearanceRequest finalized = service.finalizeReleaseOrder(REQUEST_ID, "REL-002", LocalDate.of(2026, 6, 1));

        assertThat(finalized.getStatus()).isEqualTo(ExitClearanceStatus.RELEASE_ORDER_ISSUED);
        verify(employeeReleaseService).release(eq(employee), eq(LocalDate.of(2026, 6, 1)), eq(EmployeeStatus.DECEASED), any());
    }

    @Test
    void finalizeReleaseOrder_vrs_releasesAsRetired() {
        ExitClearanceRequest request = new ExitClearanceRequest(employee, SeparationType.VRS, LocalDate.of(2026, 6, 1), null);
        ReflectionTestUtils.setField(request, "id", REQUEST_ID);
        ReflectionTestUtils.setField(request, "status", ExitClearanceStatus.CLEARANCES_COMPLETED);
        when(clearanceRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        service.finalizeReleaseOrder(REQUEST_ID, "REL-003", LocalDate.of(2026, 6, 1));

        verify(employeeReleaseService).release(eq(employee), eq(LocalDate.of(2026, 6, 1)), eq(EmployeeStatus.RETIRED), any());
    }

    @Test
    void findLatestForEmployee_returnsMostRecentByInitiatedDate() {
        ExitClearanceRequest older = new ExitClearanceRequest(employee, SeparationType.RESIGNATION, LocalDate.of(2025, 1, 1), null);
        ReflectionTestUtils.setField(older, "id", 1L);
        ReflectionTestUtils.setField(older, "initiatedDate", LocalDate.of(2025, 1, 1));
        ExitClearanceRequest newer = new ExitClearanceRequest(employee, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 1), null);
        ReflectionTestUtils.setField(newer, "id", 2L);
        ReflectionTestUtils.setField(newer, "initiatedDate", LocalDate.of(2026, 1, 1));
        when(clearanceRequestRepository.findByEmployeeId(EMPLOYEE_ID)).thenReturn(List.of(older, newer));

        Optional<ExitClearanceRequest> latest = service.findLatestForEmployee(EMPLOYEE_ID);

        assertThat(latest).isPresent();
        assertThat(latest.get().getId()).isEqualTo(2L);
    }
}
