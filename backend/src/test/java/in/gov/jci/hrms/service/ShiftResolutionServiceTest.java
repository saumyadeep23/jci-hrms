package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeShiftSchedule;
import in.gov.jci.hrms.entity.OfficeType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.ShiftMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeShiftScheduleRepository;
import in.gov.jci.hrms.repository.ShiftMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShiftResolutionServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private EmployeeShiftScheduleRepository employeeShiftScheduleRepository;
    @Mock
    private ShiftMasterRepository shiftMasterRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private ShiftResolutionService service;
    private Department department;
    private Designation designation;
    private ShiftMaster hoRo;
    private ShiftMaster dpcWeekday;
    private ShiftMaster dpcSaturday;

    @BeforeEach
    void setUp() {
        service = new ShiftResolutionService(employeeShiftScheduleRepository, shiftMasterRepository, employeeRepository);
        department = new Department("ENG", "Engineering");
        designation = new Designation("Field Officer");

        hoRo = shift("HO-RO", LocalTime.of(9, 45), LocalTime.of(18, 15), 30);
        dpcWeekday = shift("DPC_WD", LocalTime.of(10, 0), LocalTime.of(17, 30), 15);
        dpcSaturday = shift("DPC_SAT", LocalTime.of(10, 0), LocalTime.of(14, 30), 15);

        when(employeeShiftScheduleRepository.findByEmployeeIdAndScheduleDate(any(), any())).thenReturn(Optional.empty());
    }

    private ShiftMaster shift(String code, LocalTime start, LocalTime end, int grace) {
        return new ShiftMaster(code, code, start, end, grace, false, true);
    }

    private Employee employeeWithoutOffice() {
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        return employee;
    }

    private Employee employeeAtRegionalOffice(OfficeType officeType) {
        Employee employee = employeeWithoutOffice();
        RegionalOffice ro = new RegionalOffice("01", "Kolkata RO", "West Bengal", in.gov.jci.hrms.entity.CityClass.X, true);
        ReflectionTestUtils.setField(ro, "id", 10L);
        ReflectionTestUtils.setField(ro, "officeType", officeType);
        ReflectionTestUtils.setField(employee, "regionalOffice", ro);
        return employee;
    }

    private Employee employeeAtDpc() {
        Employee employee = employeeWithoutOffice();
        RegionalOffice parentRo = new RegionalOffice("02", "Parent RO", "West Bengal", in.gov.jci.hrms.entity.CityClass.Y, true);
        DepartmentalPurchaseCentre dpc = new DepartmentalPurchaseCentre(parentRo, "DPC-01", "Test DPC", "Howrah", "West Bengal", true);
        ReflectionTestUtils.setField(dpc, "id", 20L);
        ReflectionTestUtils.setField(employee, "departmentalPurchaseCentre", dpc);
        return employee;
    }

    @Test
    void resolve_headOfficeEmployee_weekday_returnsHoRoWorkingDay() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtRegionalOffice(OfficeType.HEAD_OFFICE)));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.of(hoRo));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)); // Thursday

        assertThat(result.weeklyOff()).isFalse();
        assertThat(result.shift().getShiftCode()).isEqualTo("HO-RO");
    }

    @Test
    void resolve_headOfficeEmployee_saturday_isWeeklyOffButStillCarriesReferenceShift() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtRegionalOffice(OfficeType.HEAD_OFFICE)));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.of(hoRo));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 22)); // Saturday

        assertThat(result.weeklyOff()).isTrue();
        assertThat(result.shift()).isNotNull();
        assertThat(result.shift().getShiftCode()).isEqualTo("HO-RO");
    }

    @Test
    void resolve_regionalOfficeEmployee_sunday_isWeeklyOff() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtRegionalOffice(OfficeType.REGIONAL_OFFICE)));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.of(hoRo));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 23)); // Sunday

        assertThat(result.weeklyOff()).isTrue();
    }

    @Test
    void resolve_employeeWithNoOfficePosting_defaultsToHoRoFiveDayWeek() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeWithoutOffice()));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.of(hoRo));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)); // Thursday

        assertThat(result.weeklyOff()).isFalse();
        assertThat(result.shift().getShiftCode()).isEqualTo("HO-RO");
    }

    @Test
    void resolve_dpcEmployee_weekday_returnsDpcWeekdayShift() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtDpc()));
        when(shiftMasterRepository.findByShiftCode("DPC_WD")).thenReturn(Optional.of(dpcWeekday));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)); // Thursday

        assertThat(result.weeklyOff()).isFalse();
        assertThat(result.shift().getShiftCode()).isEqualTo("DPC_WD");
    }

    @Test
    void resolve_dpcEmployee_saturday_returnsDpcSaturdayShift_notWeeklyOff() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtDpc()));
        when(shiftMasterRepository.findByShiftCode("DPC_SAT")).thenReturn(Optional.of(dpcSaturday));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 22)); // Saturday

        assertThat(result.weeklyOff()).isFalse();
        assertThat(result.shift().getShiftCode()).isEqualTo("DPC_SAT");
    }

    @Test
    void resolve_dpcEmployee_sunday_isWeeklyOff() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtDpc()));
        when(shiftMasterRepository.findByShiftCode("DPC_WD")).thenReturn(Optional.of(dpcWeekday));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 23)); // Sunday

        assertThat(result.weeklyOff()).isTrue();
        assertThat(result.shift().getShiftCode()).isEqualTo("DPC_WD");
    }

    @Test
    void resolve_explicitRosterAssignment_takesPrecedenceOverOfficeDefault() {
        Employee employee = employeeAtDpc();
        ShiftMaster watchmenShift = shift("SHIFT_C", LocalTime.of(22, 0), LocalTime.of(6, 0), 10);
        EmployeeShiftSchedule schedule = new EmployeeShiftSchedule(employee, LocalDate.of(2026, 8, 20), watchmenShift);
        when(employeeShiftScheduleRepository.findByEmployeeIdAndScheduleDate(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)))
                .thenReturn(Optional.of(schedule));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20));

        assertThat(result.weeklyOff()).isFalse();
        assertThat(result.shift().getShiftCode()).isEqualTo("SHIFT_C");
    }

    @Test
    void resolve_explicitRosterOverrideToWeeklyOff_returnsWeeklyOffWithDefaultReferenceShift() {
        Employee employee = employeeAtRegionalOffice(OfficeType.HEAD_OFFICE);
        EmployeeShiftSchedule schedule = new EmployeeShiftSchedule(employee, LocalDate.of(2026, 8, 20), null);
        when(employeeShiftScheduleRepository.findByEmployeeIdAndScheduleDate(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)))
                .thenReturn(Optional.of(schedule));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.of(hoRo));

        var result = service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20));

        assertThat(result.weeklyOff()).isTrue();
        assertThat(result.shift().getShiftCode()).isEqualTo("HO-RO");
    }

    @Test
    void resolve_whenRequiredShiftNotConfigured_throwsBusinessRuleViolationException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employeeAtRegionalOffice(OfficeType.HEAD_OFFICE)));
        when(shiftMasterRepository.findByShiftCode("HO-RO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveShiftForEmployee(EMPLOYEE_ID, LocalDate.of(2026, 8, 20)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
