package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DeputedStaffReportResponse;
import in.gov.jci.hrms.dto.SuspendedStaffReportResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import in.gov.jci.hrms.entity.EmployeeSuspensionNec;
import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.PayOption;
import in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * "PimsReportingService" as literally named in the task doesn't exist as a single class - this codebase's
 * PIMS reporting hub gives every report its own dedicated service (CadreStrengthReportService,
 * SuperannuationReportService, ...), all fanned into one PimsReportController. This test file covers both
 * of the new dedicated services (DeputedStaffReportService, SuspendedStaffReportService) under the
 * requested file name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PimsReportingServiceTest {

    @Mock private EmployeeDeputationRecordRepository deputationRepository;
    @Mock private EmployeeSuspensionRecordRepository suspensionRepository;
    @Mock private EmployeeSuspensionNecRepository necRepository;

    private DeputedStaffReportService deputedStaffReportService;
    private SuspendedStaffReportService suspendedStaffReportService;

    private Employee employee(long id, String code, String name) {
        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        Employee e = new Employee(code, name, "Rao", code.toLowerCase() + "@example.com", LocalDate.of(1990, 1, 1), department, designation);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @BeforeEach
    void setUp() {
        deputedStaffReportService = new DeputedStaffReportService(deputationRepository);
        suspendedStaffReportService = new SuspendedStaffReportService(suspensionRepository, necRepository);
    }

    @Test
    void deputedStaff_aggregatesActiveDeputationsByDirection() {
        Employee outEmp = employee(1L, "EMP-001", "Asha");
        Employee inEmp = employee(2L, "EMP-002", "Vikram");

        EmployeeDeputationRecord out = new EmployeeDeputationRecord(outEmp, DeputationDirection.DEPUTATION_OUT, "NTPC",
                "PSU", "Delhi", false, LocalDate.now().minusMonths(2), LocalDate.now().plusMonths(1),
                PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        EmployeeDeputationRecord in1 = new EmployeeDeputationRecord(inEmp, DeputationDirection.DEPUTATION_IN, "SAIL",
                "PSU", "Kolkata", true, LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(6),
                PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);

        when(deputationRepository.findAllActiveDeputations(any(LocalDate.class))).thenReturn(List.of(out, in1));

        DeputedStaffReportResponse response = deputedStaffReportService.generate(null, null, null, null);

        assertThat(response.rows()).hasSize(2);
        assertThat(response.totalDeputedOut()).isEqualTo(1);
        assertThat(response.totalDeputedIn()).isEqualTo(1);
        // "out" is due for repatriation within the next quarter (periodTo = today + 1 month).
        assertThat(response.dueForRepatriationThisQuarter()).isEqualTo(1);
    }

    @Test
    void deputedStaff_directionFilter_narrowsToOneDirection() {
        Employee outEmp = employee(1L, "EMP-001", "Asha");
        Employee inEmp = employee(2L, "EMP-002", "Vikram");
        EmployeeDeputationRecord out = new EmployeeDeputationRecord(outEmp, DeputationDirection.DEPUTATION_OUT, "NTPC",
                "PSU", "Delhi", false, LocalDate.now().minusMonths(2), LocalDate.now().plusYears(1),
                PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        EmployeeDeputationRecord in1 = new EmployeeDeputationRecord(inEmp, DeputationDirection.DEPUTATION_IN, "SAIL",
                "PSU", "Kolkata", true, LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1),
                PayOption.PARENT_CADRE_BASIC_PLUS_DEP_ALLOWANCE, null);
        when(deputationRepository.findAllActiveDeputations(any(LocalDate.class))).thenReturn(List.of(out, in1));

        DeputedStaffReportResponse response = deputedStaffReportService.generate("DEPUTATION_OUT", null, null, null);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).deputationDirection()).isEqualTo(DeputationDirection.DEPUTATION_OUT);
    }

    @Test
    void suspendedStaff_computesDaysUnderSuspensionAndCurrentMonthNecStatus() {
        Employee emp = employee(10L, "EMP-010", "Ramesh");
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(emp, "ORD/2026/1", LocalDate.now().minusDays(45),
                LocalDate.now().minusDays(40), "Delhi HQ", null);
        ReflectionTestUtils.setField(suspension, "id", 500L);

        LocalDate today = LocalDate.now();
        EmployeeSuspensionNec verifiedNec = new EmployeeSuspensionNec(suspension, emp, today.getMonthValue(), today.getYear());
        verifiedNec.setVerified(true);

        when(suspensionRepository.findActiveSuspensionsWithNecStatus(today.getMonthValue(), today.getYear())).thenReturn(List.of(suspension));
        when(necRepository.findBySuspension_IdAndSalMonthAndSalYear(500L, today.getMonthValue(), today.getYear())).thenReturn(java.util.Optional.of(verifiedNec));

        SuspendedStaffReportResponse response = suspendedStaffReportService.generate(null, null, null, null);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).daysUnderSuspension()).isEqualTo(40);
        assertThat(response.rows().get(0).currentMonthNecStatus()).isEqualTo("VERIFIED");
        assertThat(response.pendingNecThisMonth()).isEqualTo(0);
        assertThat(response.rows().get(0).isReviewOverdue()).isFalse(); // only 40 days elapsed, review not due yet
    }

    @Test
    void suspendedStaff_noNecRowForCurrentMonth_reportsNotSubmittedAndPending() {
        Employee emp = employee(11L, "EMP-011", "Suresh");
        EmployeeSuspensionRecord suspension = new EmployeeSuspensionRecord(emp, "ORD/2026/2", LocalDate.now().minusDays(120),
                LocalDate.now().minusDays(100), "Mumbai HQ", null);
        ReflectionTestUtils.setField(suspension, "id", 501L);
        // No NEC row added for the current month - necRecords stays empty.

        LocalDate today = LocalDate.now();
        when(suspensionRepository.findActiveSuspensionsWithNecStatus(today.getMonthValue(), today.getYear())).thenReturn(List.of(suspension));

        SuspendedStaffReportResponse response = suspendedStaffReportService.generate(null, null, null, null);

        assertThat(response.rows()).hasSize(1);
        assertThat(response.rows().get(0).currentMonthNecStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(response.pendingNecThisMonth()).isEqualTo(1);
        // 100 days elapsed, > 90, and reviewDate is null -> overdue.
        assertThat(response.rows().get(0).isReviewOverdue()).isTrue();
        assertThat(response.pending90DayReviews()).isEqualTo(1);
    }
}
