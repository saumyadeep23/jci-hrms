package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TourRequestRequest;
import in.gov.jci.hrms.dto.TourRequestResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.TourRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourRequestServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private TourRequestRepository tourRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private TourRequestService tourRequestService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        tourRequestService = new TourRequestService(tourRequestRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private TourRequestRequest validRequest() {
        return new TourRequestRequest("TR-2026-001", EMPLOYEE_ID, "Client visit", "Delhi", "Mumbai",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), false, null, null, null);
    }

    @Test
    void create_savesAndReturnsResponse() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(tourRequestRepository.saveAndFlush(any(TourRequest.class))).thenAnswer(inv -> {
            TourRequest saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        TourRequestResponse response = tourRequestService.create(validRequest());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.requestNumber()).isEqualTo("TR-2026-001");
        assertThat(response.destination()).isEqualTo("Mumbai");
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourRequestService.create(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void create_whenRequestNumberAlreadyInUse_throwsMasterDataConflictException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(tourRequestRepository.saveAndFlush(any(TourRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> tourRequestService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(tourRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourRequestService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
