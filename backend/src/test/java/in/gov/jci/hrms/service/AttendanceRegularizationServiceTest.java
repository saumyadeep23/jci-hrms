package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AttendanceRegularizationRequest;
import in.gov.jci.hrms.dto.AttendanceRegularizationResponse;
import in.gov.jci.hrms.dto.RegularizationDecisionRequest;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.AttendanceRegularizationApplication;
import in.gov.jci.hrms.entity.AttendanceStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveLedgerEntry;
import in.gov.jci.hrms.entity.LeaveLedgerSource;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.RegularizationReasonCode;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.AttendanceRegularizationApplicationRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceRegularizationServiceTest {

    @Mock
    private AttendanceRegularizationApplicationRepository regularizationRepository;
    @Mock
    private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SupervisorResolutionService supervisorResolutionService;

    private AttendanceRegularizationService service;
    private Employee employee;
    private DailyAttendance dailyAttendance;

    @BeforeEach
    void setUp() {
        service = new AttendanceRegularizationService(regularizationRepository, dailyAttendanceRepository,
                leaveBalanceRepository, leaveLedgerEntryRepository, employeeRepository, supervisorResolutionService);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        dailyAttendance = new DailyAttendance(employee, LocalDate.of(2026, 3, 10), AttendanceStatus.PRESENT);
        ReflectionTestUtils.setField(dailyAttendance, "id", 7L);
        dailyAttendance.applyDetail(AttendanceDetailStatus.UNAUTHORIZED_LATE, "Exceeded concessions", null, null);

        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(dailyAttendanceRepository.findByEmployeeIdAndAttendanceDate(1L, LocalDate.of(2026, 3, 10)))
                .thenReturn(Optional.of(dailyAttendance));
        lenient().when(supervisorResolutionService.resolveSupervisor(1L)).thenReturn(Optional.empty());
        lenient().when(regularizationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(dailyAttendanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(leaveLedgerEntryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(leaveBalanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submit_forIneligibleDay_throws() {
        dailyAttendance.applyDetail(AttendanceDetailStatus.PRESENT, null, null, null);
        AttendanceRegularizationRequest request = new AttendanceRegularizationRequest(1L, LocalDate.of(2026, 3, 10),
                RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch", Instant.now(), Instant.now());

        assertThatThrownBy(() -> service.submit(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void approve_withAutoPenaltyDebited_resetsToPresentAndRefundsPenalty() {
        dailyAttendance.setAutoPenaltyDebited(true);
        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.parse("2026-03-10T04:15:00Z"), Instant.parse("2026-03-10T12:45:00Z"));
        ReflectionTestUtils.setField(application, "id", 3L);
        application.setDesignatedApprover(employee);
        when(regularizationRepository.findById(3L)).thenReturn(Optional.of(application));

        LeaveType cl = new LeaveType("CL", "Casual Leave", new BigDecimal("8.0"), false, true);
        ReflectionTestUtils.setField(cl, "id", 50L);
        LeaveBalance clBalance = new LeaveBalance(employee, cl, 2026, new BigDecimal("8.0"));
        clBalance.setUsedDays(new BigDecimal("0.5"));
        LeaveLedgerEntry originalDebit = new LeaveLedgerEntry(employee, cl, LocalDate.of(2026, 3, 10),
                new BigDecimal("-0.5"), "Auto-debited", LeaveLedgerSource.AUTO_LATE_DEDUCTION);

        when(leaveLedgerEntryRepository.findByRelatedDailyAttendanceId(7L)).thenReturn(Optional.of(originalDebit));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 50L, 2026)).thenReturn(Optional.of(clBalance));

        AttendanceRegularizationResponse response = service.approve(3L, new RegularizationDecisionRequest(true, "Approved by HoD"), 1L);

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(dailyAttendance.getDetailStatus()).isEqualTo(AttendanceDetailStatus.PRESENT);
        assertThat(dailyAttendance.isRegularized()).isTrue();
        assertThat(dailyAttendance.isAutoPenaltyDebited()).isFalse();
        assertThat(clBalance.getUsedDays()).isEqualByComparingTo("0.0");
    }

    @Test
    void approve_withoutAutoPenaltyDebited_doesNotTouchLedger() {
        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.parse("2026-03-10T04:15:00Z"), Instant.parse("2026-03-10T12:45:00Z"));
        ReflectionTestUtils.setField(application, "id", 4L);
        application.setDesignatedApprover(employee);
        when(regularizationRepository.findById(4L)).thenReturn(Optional.of(application));

        service.approve(4L, new RegularizationDecisionRequest(true, "Approved"), 1L);

        org.mockito.Mockito.verifyNoInteractions(leaveLedgerEntryRepository);
    }

    @Test
    void approve_rejected_leavesDailyAttendanceUnchanged() {
        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.now(), Instant.now());
        ReflectionTestUtils.setField(application, "id", 6L);
        application.setDesignatedApprover(employee);
        when(regularizationRepository.findById(6L)).thenReturn(Optional.of(application));

        service.approve(6L, new RegularizationDecisionRequest(false, "Insufficient justification"), 1L);

        assertThat(dailyAttendance.getDetailStatus()).isEqualTo(AttendanceDetailStatus.UNAUTHORIZED_LATE);
        assertThat(dailyAttendance.isRegularized()).isFalse();
    }

    // ---- Duplicate-submission guard ----

    @Test
    void submit_pendingRequestAlreadyExistsForSameDate_throwsConflict() {
        when(regularizationRepository.existsByEmployeeIdAndAttendanceDateAndApprovalStatus(
                1L, LocalDate.of(2026, 3, 10), ApprovalStatus.PENDING)).thenReturn(true);
        AttendanceRegularizationRequest request = new AttendanceRegularizationRequest(1L, LocalDate.of(2026, 3, 10),
                RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch", Instant.now(), Instant.now());

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(in.gov.jci.hrms.exception.DuplicatePendingRegularizationException.class);

        org.mockito.Mockito.verify(regularizationRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void submit_noExistingPendingRequest_succeeds() {
        when(regularizationRepository.existsByEmployeeIdAndAttendanceDateAndApprovalStatus(
                1L, LocalDate.of(2026, 3, 10), ApprovalStatus.PENDING)).thenReturn(false);
        AttendanceRegularizationRequest request = new AttendanceRegularizationRequest(1L, LocalDate.of(2026, 3, 10),
                RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch", Instant.now(), Instant.now());

        AttendanceRegularizationResponse response = service.submit(request);

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    // ---- Approver authorization: strictly the designated approver, no role-based override ----
    // (HR_ADMIN/SUPER_ADMIN get broader READ access via findAll() - see AttendanceRegularizationController's
    // GET /all - but acting on a request is still gated on being the actual designatedApprover; the
    // controller's @PreAuthorize on GET /all is what restricts visibility, not anything the service checks.)

    @Test
    void approve_byDesignatedApprover_succeeds() {
        Employee hod = new Employee("EMP-HOD", "Head", "Officer", "hod@example.com",
                LocalDate.of(2015, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(hod, "id", 42L);

        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.now(), Instant.now());
        application.setDesignatedApprover(hod);
        ReflectionTestUtils.setField(application, "id", 8L);
        when(regularizationRepository.findById(8L)).thenReturn(Optional.of(application));

        AttendanceRegularizationResponse response = service.approve(8L, new RegularizationDecisionRequest(true, "OK"), 42L);

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void approve_byUnrelatedEmployee_throwsAccessDenied() {
        Employee hod = new Employee("EMP-HOD", "Head", "Officer", "hod@example.com",
                LocalDate.of(2015, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(hod, "id", 42L);

        AttendanceRegularizationApplication application = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.now(), Instant.now());
        application.setDesignatedApprover(hod);
        ReflectionTestUtils.setField(application, "id", 9L);
        when(regularizationRepository.findById(9L)).thenReturn(Optional.of(application));

        // Some other employee (not the designated approver) tries to decide it - even an HR_ADMIN/SUPER_ADMIN
        // caller would hit this same check, since the service has no notion of role at all, only callerId.
        assertThatThrownBy(() -> service.approve(9L, new RegularizationDecisionRequest(true, "OK"), 777L))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThat(application.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    // ---- HR_ADMIN/SUPER_ADMIN organization-wide visibility (GET /all) ----

    @Test
    void findAll_returnsEveryRequestRegardlessOfDesignatedApprover_newestFirst() {
        Employee hod = new Employee("EMP-HOD", "Head", "Officer", "hod@example.com",
                LocalDate.of(2015, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(hod, "id", 42L);

        AttendanceRegularizationApplication older = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 5), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.now(), Instant.now());
        older.setDesignatedApprover(hod);
        ReflectionTestUtils.setField(older, "id", 20L);
        ReflectionTestUtils.setField(older, "createdAt", Instant.parse("2026-03-05T00:00:00Z"));

        AttendanceRegularizationApplication newer = new AttendanceRegularizationApplication(employee,
                LocalDate.of(2026, 3, 10), dailyAttendance, RegularizationReasonCode.FORGOT_PUNCH, "Forgot to punch",
                Instant.now(), Instant.now());
        // No designated approver at all - findAll() must still surface it, unlike findAllDecidedByApprover().
        ReflectionTestUtils.setField(newer, "id", 21L);
        ReflectionTestUtils.setField(newer, "createdAt", Instant.parse("2026-03-10T00:00:00Z"));

        when(regularizationRepository.findAll()).thenReturn(java.util.List.of(older, newer));

        java.util.List<AttendanceRegularizationResponse> result = service.findAll();

        assertThat(result).extracting(AttendanceRegularizationResponse::id).containsExactly(21L, 20L);
    }
}
