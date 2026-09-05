package in.gov.jci.hrms.service;

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
import in.gov.jci.hrms.repository.HolidayRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveValidationServiceTest {

    private static final Long EMPLOYEE_ID = 1L;

    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private HolidayRepository holidayRepository;

    private LeaveValidationService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new LeaveValidationService(leaveApplicationRepository, holidayRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private LeaveType leaveType(String code) {
        LeaveType type = new LeaveType(code, code + " Leave", BigDecimal.TEN, false, true);
        ReflectionTestUtils.setField(type, "id", 5L);
        return type;
    }

    private void noAdjacentApplications() {
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(any(), any(), any())).thenReturn(List.of());
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());
    }

    // ---- endDate/startDate ----

    @Test
    void validate_endDateBeforeStartDate_throws() {
        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("EL"),
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 10), LeaveSession.FULL_DAY))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- half-day session restricted to CL ----

    @Test
    void validate_halfDaySessionOnNonCl_throws() {
        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("EL"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LeaveSession.FIRST_HALF))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only permitted for CL");
    }

    @Test
    void validate_halfDaySessionSpanningMultipleDays_throws() {
        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 11), LeaveSession.FIRST_HALF))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("startDate equal to endDate");
    }

    @Test
    void validate_halfDayClOnSingleDay_returnsHalfDay() {
        noAdjacentApplications();

        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), LeaveSession.SECOND_HALF);

        assertThat(result).isEqualByComparingTo("0.5");
    }

    // ---- 5-year continuous absence cap ----

    @Test
    void validate_exactlyFiveYears_isAllowed() {
        noAdjacentApplications();
        // EL includes intervening days rather than counting them individually - computeDebitableDays
        // never touches holidayRepository for a non-CL type, so nothing to stub here.
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusYears(5);

        assertThatCode(() -> service.validateAndComputeDebitableDays(employee, leaveType("EL"), start, end, LeaveSession.FULL_DAY))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_moreThanFiveYears_throws() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusYears(5).plusDays(1);

        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("EL"), start, end, LeaveSession.FULL_DAY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("5 years");
    }

    // ---- contiguous CL vs EL/HPL/CCL ----

    @Test
    void validate_clImmediatelyAfterApprovedEl_throws() {
        LeaveApplication existingEl = new LeaveApplication(employee, leaveType("EL"),
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 12), BigDecimal.valueOf(8), "Vacation");
        existingEl.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(existingEl));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), LeaveSession.FULL_DAY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("contiguously");
    }

    @Test
    void validate_elImmediatelyBeforeApprovedCl_throws() {
        LeaveApplication existingCl = new LeaveApplication(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), BigDecimal.ONE, "Personal");
        existingCl.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(any(), any(), any())).thenReturn(List.of());
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, LocalDate.of(2026, 3, 13)))
                .thenReturn(List.of(existingCl));

        assertThatThrownBy(() -> service.validateAndComputeDebitableDays(employee, leaveType("EL"),
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 12), LeaveSession.FULL_DAY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("contiguously");
    }

    @Test
    void validate_clImmediatelyAfterApprovedCl_isAllowed() {
        LeaveApplication existingCl = new LeaveApplication(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 12), BigDecimal.valueOf(2), "Personal");
        existingCl.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(existingCl));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(List.of());

        assertThatCode(() -> service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), LeaveSession.FULL_DAY))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_clWithGapBeforeEl_isAllowed() {
        // Existing EL ends 2026-03-10, new CL starts 2026-03-13 - a 2-day gap, not contiguous.
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(List.of());

        assertThatCode(() -> service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), LeaveSession.FULL_DAY))
                .doesNotThrowAnyException();
    }

    // ---- intervening holiday inclusion/exclusion ----

    @Test
    void computeDebitableDays_cl_excludesWeekends() {
        noAdjacentApplications();
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(List.of());

        // Fri 13 - Mon 16, 2026: 4 calendar days, Sat 14/Sun 15 excluded -> 2 debitable days.
        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 16), LeaveSession.FULL_DAY);

        assertThat(result).isEqualByComparingTo("2");
    }

    /**
     * SRS v6.1 FR-ATT.4/FR-LV.5 regression: a single continuous CL application
     * spanning a weekend (Fri 28-08-2026 to Mon 31-08-2026, no gazetted
     * holidays) must be allowed in one application and must debit only the
     * two working days, not all four calendar days.
     */
    @Test
    void computeDebitableDays_cl_fridayToMonday_srsExample_yieldsTwoDebitableDays() {
        noAdjacentApplications();
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 31)))
                .thenReturn(List.of());

        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 31), LeaveSession.FULL_DAY);

        assertThat(result).isEqualByComparingTo("2");
    }

    // ---- CL/RH combination (SRS v6.1 item 3 - RH is not in the contiguous-restricted set) ----

    @Test
    void validate_clImmediatelyAfterApprovedRh_isAllowed() {
        LeaveApplication existingRh = new LeaveApplication(employee, leaveType("RH"),
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12), BigDecimal.ONE, "Restricted holiday");
        existingRh.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndEndDate(
                EMPLOYEE_ID, LeaveApplicationStatus.APPROVED, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(existingRh));
        when(leaveApplicationRepository.findByEmployeeIdAndStatusAndStartDate(any(), any(), any())).thenReturn(List.of());
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(List.of());

        assertThatCode(() -> service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 13), LeaveSession.FULL_DAY))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_rhImmediatelyBeforeApprovedCl_isAllowed() {
        // RH is neither CL nor in {EL,HPL,CCL}, so validateNoContiguousClAndStatutoryLeave
        // short-circuits without touching the repository - nothing to stub.
        assertThatCode(() -> service.validateAndComputeDebitableDays(employee, leaveType("RH"),
                LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12), LeaveSession.FULL_DAY))
                .doesNotThrowAnyException();
    }

    @Test
    void computeDebitableDays_el_includesWeekends() {
        noAdjacentApplications();

        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("EL"),
                LocalDate.of(2026, 3, 13), LocalDate.of(2026, 3, 16), LeaveSession.FULL_DAY);

        assertThat(result).isEqualByComparingTo("4");
    }

    @Test
    void computeDebitableDays_cl_excludesGazettedHoliday() {
        noAdjacentApplications();
        LocalDate holidayDate = LocalDate.of(2026, 3, 11); // Wednesday
        when(holidayRepository.findByHolidayDateBetween(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(new Holiday(holidayDate, "Test Holiday", HolidayType.GAZETTED)));

        // Tue 10 - Thu 12: 3 calendar days, Wed 11 is a holiday -> 2 debitable days.
        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("CL"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), LeaveSession.FULL_DAY);

        assertThat(result).isEqualByComparingTo("2");
    }

    @Test
    void computeDebitableDays_commuted_includesGazettedHoliday() {
        // COMMUTED is neither CL nor in the {EL,HPL,CCL} contiguous-restricted set,
        // so the contiguous-leave check short-circuits without touching the repository.

        BigDecimal result = service.validateAndComputeDebitableDays(employee, leaveType("COMMUTED"),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), LeaveSession.FULL_DAY);

        assertThat(result).isEqualByComparingTo("3");
    }

    // ---- validateTotalDaysMatches ----

    @Test
    void validateTotalDaysMatches_withinTolerance_doesNotThrow() {
        assertThatCode(() -> service.validateTotalDaysMatches(new BigDecimal("3.0"), new BigDecimal("3.02")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateTotalDaysMatches_outsideTolerance_throws() {
        assertThatThrownBy(() -> service.validateTotalDaysMatches(new BigDecimal("3.0"), new BigDecimal("2.0")))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not match");
    }
}
