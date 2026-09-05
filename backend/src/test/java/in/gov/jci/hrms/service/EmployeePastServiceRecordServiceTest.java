package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.PastServiceRecordResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataValidationException;
import in.gov.jci.hrms.repository.EmployeePastServiceRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeePastServiceRecordServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private EmployeePastServiceRecordRepository pastServiceRecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private EmployeePastServiceRecordService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new EmployeePastServiceRecordService(pastServiceRecordRepository, employeeRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Backend Developer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private PastServiceRecordRequest validRequest() {
        return new PastServiceRecordRequest("Tata Consultancy Services", PastServiceOrganizationType.PRIVATE_SECTOR,
                "Software Engineer", LocalDate.of(2018, 6, 1), LocalDate.of(2023, 12, 31),
                null, null, null, false, null, "Better opportunity", null, "TCS-NOC-991");
    }

    @Test
    void create_whenEmployeeFound_savesAndReturnsResponse() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(pastServiceRecordRepository.saveAndFlush(any(EmployeePastServiceRecord.class)))
                .thenAnswer(invocation -> {
                    EmployeePastServiceRecord record = invocation.getArgument(0);
                    ReflectionTestUtils.setField(record, "id", 3L);
                    return record;
                });

        PastServiceRecordResponse response = service.create(EMPLOYEE_ID, validRequest());

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.employeeId()).isEqualTo(EMPLOYEE_ID);
        assertThat(response.organizationName()).isEqualTo("Tata Consultancy Services");
    }

    @Test
    void create_whenToDateBeforeFromDate_throwsMasterDataValidationException() {
        PastServiceRecordRequest invalid = new PastServiceRecordRequest("Infosys", PastServiceOrganizationType.PRIVATE_SECTOR,
                "Engineer", LocalDate.of(2020, 1, 1), LocalDate.of(2019, 1, 1),
                null, null, null, false, null, null, null, null);

        assertThatThrownBy(() -> service.create(EMPLOYEE_ID, invalid))
                .isInstanceOf(MasterDataValidationException.class);
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(99L, validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void verify_whenFound_marksVerified() {
        EmployeePastServiceRecord record = new EmployeePastServiceRecord(
                employee, "Wipro", PastServiceOrganizationType.PRIVATE_SECTOR, "Engineer",
                LocalDate.of(2015, 1, 1), LocalDate.of(2017, 1, 1));
        ReflectionTestUtils.setField(record, "id", 5L);
        when(pastServiceRecordRepository.findById(5L)).thenReturn(Optional.of(record));
        when(pastServiceRecordRepository.saveAndFlush(any(EmployeePastServiceRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PastServiceRecordResponse response = service.verify(EMPLOYEE_ID, 5L, "hr-admin");

        assertThat(response.verified()).isTrue();
        assertThat(response.verifiedBy()).isEqualTo("hr-admin");
    }

    @Test
    void delete_whenFound_softDeletesRatherThanHardDeleting() {
        EmployeePastServiceRecord record = new EmployeePastServiceRecord(
                employee, "Wipro", PastServiceOrganizationType.PRIVATE_SECTOR, "Engineer",
                LocalDate.of(2015, 1, 1), LocalDate.of(2017, 1, 1));
        ReflectionTestUtils.setField(record, "id", 4L);
        when(pastServiceRecordRepository.findById(4L)).thenReturn(Optional.of(record));

        service.delete(EMPLOYEE_ID, 4L);

        assertThat(record.getDeletedAt()).isNotNull();
    }
}
