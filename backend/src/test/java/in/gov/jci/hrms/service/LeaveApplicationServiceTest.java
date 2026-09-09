package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.LeaveApplicationPreviewRequest;
import in.gov.jci.hrms.dto.LeaveApplicationPreviewResponse;
import in.gov.jci.hrms.dto.LeaveApplicationRequest;
import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import in.gov.jci.hrms.entity.LeaveBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveApplicationActionRepository;
import in.gov.jci.hrms.repository.LeaveApplicationRepository;
import in.gov.jci.hrms.repository.LeaveBalanceRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveApplicationServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long LEAVE_TYPE_ID = 2L;
    private static final Long APPLICATION_ID = 3L;
    private static final Long HPL_LEAVE_TYPE_ID = 31L;
    private static final int YEAR = 2026;

    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private SupervisorResolutionService supervisorResolutionService;
    @Mock
    private LeaveValidationService leaveValidationService;
    @Mock
    private LeaveApplicationActionRepository leaveApplicationActionRepository;

    private LeaveApplicationService leaveApplicationService;

    private Employee employee;
    private LeaveType leaveType;

    @BeforeEach
    void setUp() {
        leaveApplicationService = new LeaveApplicationService(leaveApplicationRepository, leaveTypeRepository,
                leaveBalanceRepository, employeeRepository, supervisorResolutionService, leaveValidationService,
                leaveApplicationActionRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Field Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2024, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        leaveType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), false, true);
        ReflectionTestUtils.setField(leaveType, "id", LEAVE_TYPE_ID);
    }

    private LeaveApplicationRequest validRequest() {
        return new LeaveApplicationRequest(EMPLOYEE_ID, LEAVE_TYPE_ID,
                LocalDate.of(YEAR, 3, 10), LocalDate.of(YEAR, 3, 12), new BigDecimal("3.0"), "Family event", null);
    }

    private LeaveApplication applicationFrom(Long id, LeaveApplicationRequest request) {
        LeaveApplication application = new LeaveApplication(employee, leaveType, request.startDate(),
                request.endDate(), request.totalDays(), request.reason());
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    private LeaveBalance balanceOf(BigDecimal credited, BigDecimal used, BigDecimal reserved) {
        LeaveBalance balance = new LeaveBalance(employee, leaveType, YEAR, credited);
        balance.setUsedDays(used);
        balance.setReservedDays(reserved);
        return balance;
    }

    // ---- create ----

    @Test
    void create_savesAsDraftWithNoBalanceImpact() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("3.0"));
        when(leaveApplicationRepository.saveAndFlush(any(LeaveApplication.class))).thenAnswer(inv -> {
            LeaveApplication saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", APPLICATION_ID);
            return saved;
        });

        LeaveApplicationResponse response = leaveApplicationService.create(validRequest());

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.DRAFT);
        assertThat(response.totalDays()).isEqualByComparingTo("3.0");
        org.mockito.Mockito.verifyNoInteractions(leaveBalanceRepository);
        org.mockito.Mockito.verifyNoInteractions(supervisorResolutionService);
    }

    @Test
    void create_persistsTheServerComputedDebitableDays_notTheClientSuppliedFigure() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        // Client supplied 3.0 (within the validation service's tolerance of whatever it computes) but the
        // server's own computed figure - 2.5, say, if a holiday fell inside the range - is authoritative.
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("2.5"));
        when(leaveApplicationRepository.saveAndFlush(any(LeaveApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        LeaveApplicationResponse response = leaveApplicationService.create(validRequest());

        assertThat(response.totalDays()).isEqualByComparingTo("2.5");
    }

    @Test
    void create_whenValidationServiceRejects_propagatesException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleViolationException("endDate must not be before startDate"));

        assertThatThrownBy(() -> leaveApplicationService.create(validRequest()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void create_whenTotalDaysMismatch_propagatesException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("2.0"));
        org.mockito.Mockito.doThrow(new BusinessRuleViolationException("totalDays does not match"))
                .when(leaveValidationService).validateTotalDaysMatches(any(), any());

        assertThatThrownBy(() -> leaveApplicationService.create(validRequest()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.create(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void create_whenLeaveTypeMissing_throwsMasterDataNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.create(validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    // ---- update ----

    @Test
    void update_onDraft_reValidatesAndAppliesEditedFields() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        LeaveType newLeaveType = new LeaveType("CL", "Casual Leave", new BigDecimal("8.0"), false, true);
        ReflectionTestUtils.setField(newLeaveType, "id", 9L);
        LeaveApplicationRequest editedRequest = new LeaveApplicationRequest(EMPLOYEE_ID, 9L,
                LocalDate.of(YEAR, 4, 1), LocalDate.of(YEAR, 4, 2), new BigDecimal("2.0"), "Updated reason", null);

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findById(9L)).thenReturn(Optional.of(newLeaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("2.0"));

        LeaveApplicationResponse response = leaveApplicationService.update(APPLICATION_ID, editedRequest);

        assertThat(response.leaveTypeId()).isEqualTo(9L);
        assertThat(response.startDate()).isEqualTo(LocalDate.of(YEAR, 4, 1));
        assertThat(response.endDate()).isEqualTo(LocalDate.of(YEAR, 4, 2));
        assertThat(response.totalDays()).isEqualByComparingTo("2.0");
        assertThat(response.reason()).isEqualTo("Updated reason");
        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.DRAFT);
    }

    @Test
    void update_whenNotDraft_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.update(APPLICATION_ID, validRequest()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void update_whenValidationServiceRejects_propagatesExceptionWithoutMutatingApplication() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        LocalDate originalStart = application.getStartDate();
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleViolationException("endDate must not be before startDate"));

        assertThatThrownBy(() -> leaveApplicationService.update(APPLICATION_ID, validRequest()))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(application.getStartDate()).isEqualTo(originalStart);
    }

    @Test
    void update_whenApplicationMissing_throwsMasterDataNotFoundException() {
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.update(APPLICATION_ID, validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    // ---- preview ----

    @Test
    void preview_delegatesToValidationServiceAndReturnsCalendarAndDebitableDays() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("3.0"));

        LeaveApplicationPreviewRequest request = new LeaveApplicationPreviewRequest(EMPLOYEE_ID, LEAVE_TYPE_ID,
                LocalDate.of(YEAR, 3, 10), LocalDate.of(YEAR, 3, 12), null);

        LeaveApplicationPreviewResponse response = leaveApplicationService.preview(request);

        assertThat(response.valid()).isTrue();
        assertThat(response.calendarDays()).isEqualByComparingTo("3");
        assertThat(response.debitableDays()).isEqualByComparingTo("3.0");
        assertThat(response.message()).isNull();
    }

    @Test
    void preview_whenValidationFails_returnsInvalidWithMessageInsteadOfThrowing() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(LEAVE_TYPE_ID)).thenReturn(Optional.of(leaveType));
        when(leaveValidationService.validateAndComputeDebitableDays(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessRuleViolationException("CL cannot be applied contiguously before/after EL"));

        LeaveApplicationPreviewRequest request = new LeaveApplicationPreviewRequest(EMPLOYEE_ID, LEAVE_TYPE_ID,
                LocalDate.of(YEAR, 3, 10), LocalDate.of(YEAR, 3, 12), null);

        LeaveApplicationPreviewResponse response = leaveApplicationService.preview(request);

        assertThat(response.valid()).isFalse();
        assertThat(response.debitableDays()).isNull();
        assertThat(response.message()).contains("contiguously");
        assertThat(response.calendarDays()).isEqualByComparingTo("3"); // still reported even on failure
    }

    // ---- submit ----

    @Test
    void submit_withSufficientBalance_reservesRequestedDaysAndResolvesApprover() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("0.0"));

        PostMaster approverPost = new PostMaster("PC-002", "Manager", employee.getDepartment(), employee.getDesignation(), true);
        ReflectionTestUtils.setField(approverPost, "id", 20L);
        Employee approver = new Employee("EMP-020", "Ravi", "Kumar", "ravi.kumar@example.com",
                LocalDate.of(2020, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(approver, "id", 20L);

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));
        when(supervisorResolutionService.resolveSupervisor(EMPLOYEE_ID))
                .thenReturn(Optional.of(new SupervisorResolutionService.SupervisorResolution(approver, approverPost)));

        LeaveApplicationResponse response = leaveApplicationService.submit(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.PENDING_APPROVAL);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("3.0");
        assertThat(response.approverEmployeeId()).isEqualTo(20L);
        assertThat(response.approverPostId()).isEqualTo(20L);
    }

    @Test
    void submit_whenNoApproverResolvable_stillSubmitsWithNullApprover() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("0.0"), new BigDecimal("0.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));
        when(supervisorResolutionService.resolveSupervisor(EMPLOYEE_ID)).thenReturn(Optional.empty());

        LeaveApplicationResponse response = leaveApplicationService.submit(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.PENDING_APPROVAL);
        assertThat(response.approverEmployeeId()).isNull();
        assertThat(balance.getReservedDays()).isEqualByComparingTo("3.0");
    }

    @Test
    void submit_withInsufficientBalance_throwsInsufficientLeaveBalanceExceptionAndDoesNotReserve() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("28.0"), new BigDecimal("0.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        // A subtype of BusinessRuleViolationException specifically, not just any BusinessRuleViolationException -
        // GlobalExceptionHandler maps this one to 422 rather than the generic 400.
        assertThatThrownBy(() -> leaveApplicationService.submit(APPLICATION_ID))
                .isInstanceOf(InsufficientLeaveBalanceException.class)
                .hasMessageContaining("Insufficient");

        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");
        assertThat(application.getStatus()).isEqualTo(LeaveApplicationStatus.DRAFT);
    }

    @Test
    void submit_whenNoBalanceProvisioned_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.submit(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("No leave balance provisioned");
    }

    @Test
    void submit_whenNotInDraftStatus_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.submit(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- approve ----

    @Test
    void approve_movesReservedDaysIntoUsedDays() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        LeaveApplicationResponse response = leaveApplicationService.approve(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.APPROVED);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.0");
    }

    @Test
    void approve_whenNotPendingApproval_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.approve(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- reject ----

    @Test
    void reject_releasesReservedDaysBackToZero() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        LeaveApplicationResponse response = leaveApplicationService.reject(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.REJECTED);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");
        assertThat(balance.getUsedDays()).isEqualByComparingTo("2.0");
    }

    @Test
    void reject_whenNotPendingApproval_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.reject(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- cancel ----

    @Test
    void cancel_fromDraft_hasNoBalanceImpact() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        LeaveApplicationResponse response = leaveApplicationService.cancel(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.CANCELLED);
        org.mockito.Mockito.verifyNoInteractions(leaveBalanceRepository);
    }

    @Test
    void cancel_fromPendingApproval_releasesReservedDays() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("0.0"), new BigDecimal("3.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        LeaveApplicationResponse response = leaveApplicationService.cancel(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.CANCELLED);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");
    }

    @Test
    void cancel_fromApproved_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.APPROVED);
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.cancel(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- Commuted Leave: every balance touch actually debits 2x from HPL, not from Commuted's own balance ----

    private LeaveType commutedLeaveType() {
        LeaveType type = new LeaveType("COMMUTED", "Commuted Leave", BigDecimal.ZERO, false, true);
        ReflectionTestUtils.setField(type, "id", 30L);
        return type;
    }

    private LeaveType hplLeaveType() {
        LeaveType type = new LeaveType("HPL", "Half Pay Leave", new BigDecimal("20.0"), false, true);
        ReflectionTestUtils.setField(type, "id", HPL_LEAVE_TYPE_ID);
        return type;
    }

    private LeaveApplication commutedApplication(BigDecimal totalDays) {
        LeaveApplication application = new LeaveApplication(employee, commutedLeaveType(), LocalDate.of(YEAR, 3, 10),
                LocalDate.of(YEAR, 3, 11), totalDays, "Commuted leave request");
        ReflectionTestUtils.setField(application, "id", APPLICATION_ID);
        return application;
    }

    @Test
    void submit_commutedLeave_reservesDoubleFromHplBalance_applicationStillShowsCommuted() {
        LeaveApplication application = commutedApplication(new BigDecimal("2.0"));
        LeaveBalance hplBalance = new LeaveBalance(employee, hplLeaveType(), YEAR, new BigDecimal("20.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.of(hplLeaveType()));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(hplBalance));
        when(supervisorResolutionService.resolveSupervisor(EMPLOYEE_ID)).thenReturn(Optional.empty());

        LeaveApplicationResponse response = leaveApplicationService.submit(APPLICATION_ID);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.PENDING_APPROVAL);
        assertThat(response.leaveTypeCode()).isEqualTo("COMMUTED");
        assertThat(hplBalance.getReservedDays()).isEqualByComparingTo("4.0");
    }

    @Test
    void approve_commutedLeave_movesDoubleFromHplReservedToUsed() {
        LeaveApplication application = commutedApplication(new BigDecimal("2.0"));
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance hplBalance = new LeaveBalance(employee, hplLeaveType(), YEAR, new BigDecimal("20.0"));
        hplBalance.setReservedDays(new BigDecimal("4.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.of(hplLeaveType()));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(hplBalance));

        leaveApplicationService.approve(APPLICATION_ID);

        assertThat(hplBalance.getReservedDays()).isEqualByComparingTo("0.0");
        assertThat(hplBalance.getUsedDays()).isEqualByComparingTo("4.0");
    }

    @Test
    void cancel_commutedLeavePendingApproval_releasesDoubleFromHpl() {
        LeaveApplication application = commutedApplication(new BigDecimal("2.0"));
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance hplBalance = new LeaveBalance(employee, hplLeaveType(), YEAR, new BigDecimal("20.0"));
        hplBalance.setReservedDays(new BigDecimal("4.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.of(hplLeaveType()));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, HPL_LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(hplBalance));

        leaveApplicationService.cancel(APPLICATION_ID);

        assertThat(hplBalance.getReservedDays()).isEqualByComparingTo("0.0");
    }

    @Test
    void submit_commutedLeave_whenHplNotConfigured_throwsBusinessRuleViolationException() {
        LeaveApplication application = commutedApplication(new BigDecimal("2.0"));

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveTypeRepository.findByCode("HPL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.submit(APPLICATION_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- multi-tier routing: forward / sanction / rejectWithRemarks / getRoutingHistory / getSanctionsHistory ----

    private Employee employeeWithId(long id, String code, String firstName) {
        Employee e = new Employee(code, firstName, "Test", firstName.toLowerCase() + "@example.com",
                LocalDate.of(2020, 1, 1), employee.getDepartment(), employee.getDesignation());
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    @Test
    void forward_whenPendingApproval_movesCurrentAssignedToAndLogsRecommendForward() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        Employee actor = employeeWithId(20L, "EMP-020", "Ravi");
        Employee forwardedTo = employeeWithId(99L, "EMP-099", "Meera");
        application.setCurrentAssignedTo(actor);

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(employeeRepository.findById(99L)).thenReturn(Optional.of(forwardedTo));
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(actor));

        LeaveApplicationResponse response = leaveApplicationService.forward(APPLICATION_ID, 99L, "Please review", 20L);

        assertThat(response.workflowStage()).isEqualTo(in.gov.jci.hrms.entity.LeaveWorkflowStage.RECOMMENDED);
        assertThat(response.currentAssignedToEmployeeId()).isEqualTo(99L);
        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.PENDING_APPROVAL);

        org.mockito.ArgumentCaptor<in.gov.jci.hrms.entity.LeaveApplicationAction> captor =
                org.mockito.ArgumentCaptor.forClass(in.gov.jci.hrms.entity.LeaveApplicationAction.class);
        org.mockito.Mockito.verify(leaveApplicationActionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(in.gov.jci.hrms.entity.LeaveActionType.RECOMMEND_FORWARD);
        assertThat(captor.getValue().getActionBy().getId()).isEqualTo(20L);
        assertThat(captor.getValue().getForwardedTo().getId()).isEqualTo(99L);
        assertThat(captor.getValue().getRemarks()).isEqualTo("Please review");
    }

    @Test
    void forward_whenNotPendingApproval_throwsBusinessRuleViolationException() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> leaveApplicationService.forward(APPLICATION_ID, 99L, "remarks", 20L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void sanction_withActingEmployee_debitsBalanceAndLogsSanctionAction() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));
        Employee actor = employeeWithId(20L, "EMP-020", "Ravi");

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(actor));

        LeaveApplicationResponse response = leaveApplicationService.sanction(APPLICATION_ID, "Approved as recommended", 20L);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.APPROVED);
        assertThat(response.workflowStage()).isEqualTo(in.gov.jci.hrms.entity.LeaveWorkflowStage.SANCTIONED);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.0");

        org.mockito.ArgumentCaptor<in.gov.jci.hrms.entity.LeaveApplicationAction> captor =
                org.mockito.ArgumentCaptor.forClass(in.gov.jci.hrms.entity.LeaveApplicationAction.class);
        org.mockito.Mockito.verify(leaveApplicationActionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(in.gov.jci.hrms.entity.LeaveActionType.SANCTION);
        assertThat(captor.getValue().getRemarks()).isEqualTo("Approved as recommended");
    }

    @Test
    void sanction_withoutActingEmployeeId_fallsBackToCurrentAssignedToAsActor() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));
        Employee assignee = employeeWithId(20L, "EMP-020", "Ravi");
        application.setCurrentAssignedTo(assignee);

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));

        leaveApplicationService.sanction(APPLICATION_ID, null, null);

        org.mockito.ArgumentCaptor<in.gov.jci.hrms.entity.LeaveApplicationAction> captor =
                org.mockito.ArgumentCaptor.forClass(in.gov.jci.hrms.entity.LeaveApplicationAction.class);
        org.mockito.Mockito.verify(leaveApplicationActionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionBy().getId()).isEqualTo(20L);
    }

    @Test
    void rejectWithRemarks_whenRemarksBlank_throwsBusinessRuleViolationExceptionWithoutLoadingApplication() {
        assertThatThrownBy(() -> leaveApplicationService.rejectWithRemarks(APPLICATION_ID, "  ", 20L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Remarks are mandatory");

        org.mockito.Mockito.verifyNoInteractions(leaveApplicationRepository);
    }

    @Test
    void rejectWithRemarks_withRemarks_releasesReservationAndLogsRejectAction() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        application.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        LeaveBalance balance = balanceOf(new BigDecimal("30.0"), new BigDecimal("2.0"), new BigDecimal("3.0"));
        Employee actor = employeeWithId(20L, "EMP-020", "Ravi");

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(EMPLOYEE_ID, LEAVE_TYPE_ID, YEAR))
                .thenReturn(Optional.of(balance));
        when(employeeRepository.findById(20L)).thenReturn(Optional.of(actor));

        LeaveApplicationResponse response = leaveApplicationService.rejectWithRemarks(APPLICATION_ID, "Not eligible", 20L);

        assertThat(response.status()).isEqualTo(LeaveApplicationStatus.REJECTED);
        assertThat(response.workflowStage()).isEqualTo(in.gov.jci.hrms.entity.LeaveWorkflowStage.REJECTED);
        assertThat(balance.getReservedDays()).isEqualByComparingTo("0.0");

        org.mockito.ArgumentCaptor<in.gov.jci.hrms.entity.LeaveApplicationAction> captor =
                org.mockito.ArgumentCaptor.forClass(in.gov.jci.hrms.entity.LeaveApplicationAction.class);
        org.mockito.Mockito.verify(leaveApplicationActionRepository).save(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo(in.gov.jci.hrms.entity.LeaveActionType.REJECT);
        assertThat(captor.getValue().getRemarks()).isEqualTo("Not eligible");
    }

    @Test
    void getRoutingHistory_mapsActionsInChronologicalOrder() {
        LeaveApplication application = applicationFrom(APPLICATION_ID, validRequest());
        Employee submitter = employee;
        Employee approver = employeeWithId(20L, "EMP-020", "Ravi");

        in.gov.jci.hrms.entity.LeaveApplicationAction submit = new in.gov.jci.hrms.entity.LeaveApplicationAction(
                application, submitter, in.gov.jci.hrms.entity.LeaveActionType.SUBMIT, null, null);
        in.gov.jci.hrms.entity.LeaveApplicationAction sanction = new in.gov.jci.hrms.entity.LeaveApplicationAction(
                application, approver, in.gov.jci.hrms.entity.LeaveActionType.SANCTION, null, "Approved");

        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(leaveApplicationActionRepository.findByApplicationIdOrderByCreatedAtAsc(APPLICATION_ID))
                .thenReturn(java.util.List.of(submit, sanction));

        java.util.List<in.gov.jci.hrms.dto.LeaveRoutingActionResponse> history =
                leaveApplicationService.getRoutingHistory(APPLICATION_ID);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).actionType()).isEqualTo(in.gov.jci.hrms.entity.LeaveActionType.SUBMIT);
        assertThat(history.get(1).actionType()).isEqualTo(in.gov.jci.hrms.entity.LeaveActionType.SANCTION);
        assertThat(history.get(1).actionByName()).isEqualTo(approver.getFullName());
        assertThat(history.get(1).remarks()).isEqualTo("Approved");
    }

    @Test
    void getRoutingHistory_whenApplicationMissing_throwsMasterDataNotFoundException() {
        when(leaveApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaveApplicationService.getRoutingHistory(APPLICATION_ID))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void getSanctionsHistory_filtersByYearMonthAndStatus() {
        LeaveApplication inMonthApproved = applicationFrom(1L, validRequest());
        inMonthApproved.setStatus(LeaveApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(inMonthApproved, "updatedAt", java.time.Instant.parse("2026-03-15T10:00:00Z"));

        LeaveApplication inMonthRejected = applicationFrom(2L, validRequest());
        inMonthRejected.setStatus(LeaveApplicationStatus.REJECTED);
        ReflectionTestUtils.setField(inMonthRejected, "updatedAt", java.time.Instant.parse("2026-03-20T10:00:00Z"));

        LeaveApplication outOfMonth = applicationFrom(3L, validRequest());
        outOfMonth.setStatus(LeaveApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(outOfMonth, "updatedAt", java.time.Instant.parse("2026-04-01T10:00:00Z"));

        LeaveApplication stillPending = applicationFrom(4L, validRequest());
        stillPending.setStatus(LeaveApplicationStatus.PENDING_APPROVAL);
        ReflectionTestUtils.setField(stillPending, "updatedAt", java.time.Instant.parse("2026-03-16T10:00:00Z"));

        when(leaveApplicationRepository.findAll())
                .thenReturn(java.util.List.of(inMonthApproved, inMonthRejected, outOfMonth, stillPending));
        when(leaveApplicationActionRepository.findByApplicationIdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(java.util.List.of());

        java.util.List<in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse> approvedOnly =
                leaveApplicationService.getSanctionsHistory(YEAR, 3, "APPROVED");
        assertThat(approvedOnly).extracting(in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse::id).containsExactly(1L);

        java.util.List<in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse> wholeMonthAllStatuses =
                leaveApplicationService.getSanctionsHistory(YEAR, 3, null);
        assertThat(wholeMonthAllStatuses).extracting(in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse::id)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getSanctionsHistory_derivesForwardedByAndSanctionedByFromActionLog() {
        LeaveApplication application = applicationFrom(1L, validRequest());
        application.setStatus(LeaveApplicationStatus.APPROVED);
        ReflectionTestUtils.setField(application, "updatedAt", java.time.Instant.parse("2026-03-15T10:00:00Z"));

        Employee forwarder = employeeWithId(20L, "EMP-020", "Ravi");
        Employee sanctioner = employeeWithId(30L, "EMP-030", "Meena");
        in.gov.jci.hrms.entity.LeaveApplicationAction forwardAction = new in.gov.jci.hrms.entity.LeaveApplicationAction(
                application, forwarder, in.gov.jci.hrms.entity.LeaveActionType.RECOMMEND_FORWARD, sanctioner, "fwd");
        in.gov.jci.hrms.entity.LeaveApplicationAction sanctionAction = new in.gov.jci.hrms.entity.LeaveApplicationAction(
                application, sanctioner, in.gov.jci.hrms.entity.LeaveActionType.SANCTION, null, "ok");

        when(leaveApplicationRepository.findAll()).thenReturn(java.util.List.of(application));
        when(leaveApplicationActionRepository.findByApplicationIdOrderByCreatedAtAsc(1L))
                .thenReturn(java.util.List.of(forwardAction, sanctionAction));

        java.util.List<in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse> history =
                leaveApplicationService.getSanctionsHistory(YEAR, 3, null);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).forwardedByName()).isEqualTo(forwarder.getFullName());
        assertThat(history.get(0).sanctionedByName()).isEqualTo(sanctioner.getFullName());
    }
}
