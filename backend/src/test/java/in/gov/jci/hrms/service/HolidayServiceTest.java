package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.HolidayCalendarResponse;
import in.gov.jci.hrms.dto.HolidayRequest;
import in.gov.jci.hrms.dto.HolidayResponse;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DepartmentalPurchaseCentre;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.Holiday;
import in.gov.jci.hrms.entity.HolidayType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.HolidayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private HolidayService holidayService;

    @BeforeEach
    void setUp() {
        holidayService = new HolidayService(holidayRepository, employeeRepository);
    }

    private HolidayRequest validRequest() {
        return new HolidayRequest(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
    }

    private Holiday entityFrom(Long id, HolidayRequest request) {
        Holiday holiday = new Holiday(request.holidayDate(), request.name(), request.holidayType(), request.state());
        ReflectionTestUtils.setField(holiday, "id", id);
        return holiday;
    }

    private Employee employeeAt(RegionalOffice ro, DepartmentalPurchaseCentre dpc) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        Employee employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        employee.setRegionalOffice(ro);
        employee.setDepartmentalPurchaseCentre(dpc);
        return employee;
    }

    @Test
    void create_savesAndReturnsResponse() {
        HolidayRequest request = validRequest();
        when(holidayRepository.saveAndFlush(any(Holiday.class))).thenReturn(entityFrom(1L, request));

        HolidayResponse response = holidayService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Republic Day");
        assertThat(response.holidayType()).isEqualTo(HolidayType.GAZETTED);
    }

    @Test
    void create_whenDateAlreadyTaken_throwsMasterDataConflictException() {
        when(holidayRepository.saveAndFlush(any(Holiday.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> holidayService.create(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(holidayRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holidayService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class)
                .hasMessageContaining("Holiday");
    }

    @Test
    void delete_softDeletes() {
        Holiday holiday = entityFrom(2L, validRequest());
        when(holidayRepository.findById(2L)).thenReturn(Optional.of(holiday));

        holidayService.delete(2L);

        assertThat(holiday.getDeletedAt()).isNotNull();
    }

    @Test
    void getMyCalendar_forRoEmployee_includesNationalAndMatchingStateOnly() {
        RegionalOffice ro = new RegionalOffice("RO-WB", "Kolkata RO", "West Bengal", CityClass.X, true);
        Employee employee = employeeAt(ro, null);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        Holiday national = new Holiday(LocalDate.of(2026, 1, 26), "Republic Day", HolidayType.GAZETTED, null);
        Holiday sameState = new Holiday(LocalDate.of(2026, 1, 23), "Netaji Jayanti", HolidayType.GAZETTED, "West Bengal");
        Holiday otherState = new Holiday(LocalDate.of(2026, 1, 14), "Pongal", HolidayType.GAZETTED, "Tamil Nadu");
        Holiday rhSameState = new Holiday(LocalDate.of(2026, 1, 5), "Bengali New Year RH", HolidayType.RESTRICTED, "West Bengal");
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(national, sameState, otherState, rhSameState));

        HolidayCalendarResponse response = holidayService.getMyCalendar(EMPLOYEE_ID, 2026, 1);

        assertThat(response.officeLabel()).isEqualTo("RO - Kolkata RO, West Bengal");
        assertThat(response.state()).isEqualTo("West Bengal");
        assertThat(response.holidays()).extracting(HolidayResponse::name)
                .containsExactlyInAnyOrder("Republic Day", "Netaji Jayanti", "Bengali New Year RH")
                .doesNotContain("Pongal");
    }

    @Test
    void getMyCalendar_forHoEmployee_defaultsToWestBengal() {
        Employee employee = employeeAt(null, null);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of());

        HolidayCalendarResponse response = holidayService.getMyCalendar(EMPLOYEE_ID, 2026, 3);

        assertThat(response.officeLabel()).isEqualTo("HO - Kolkata, West Bengal");
        assertThat(response.state()).isEqualTo("West Bengal");
    }

    @Test
    void getMyCalendar_picksNearestUpcomingApplicableHoliday() {
        RegionalOffice ro = new RegionalOffice("RO-WB", "Kolkata RO", "West Bengal", CityClass.X, true);
        Employee employee = employeeAt(ro, null);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        // Dates relative to the real "today" (getMyCalendar uses LocalDate.now(), not an injectable Clock).
        LocalDate today = LocalDate.now();
        Holiday farOtherState = new Holiday(today.plusDays(5), "Pongal", HolidayType.GAZETTED, "Tamil Nadu");
        Holiday nearApplicable = new Holiday(today.plusDays(10), "Republic Day", HolidayType.GAZETTED, null);
        when(holidayRepository.findByHolidayDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(farOtherState, nearApplicable));

        HolidayCalendarResponse response = holidayService.getMyCalendar(EMPLOYEE_ID, today.getYear(), today.getMonthValue());

        assertThat(response.upcomingHolidayName()).isEqualTo("Republic Day");
        assertThat(response.upcomingHolidayDate()).isEqualTo(today.plusDays(10));
    }
}
