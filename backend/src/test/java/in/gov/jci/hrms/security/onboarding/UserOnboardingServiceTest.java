package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.InvitationStatus;
import in.gov.jci.hrms.entity.UserAccountStatus;
import in.gov.jci.hrms.entity.UserInvitation;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserInvitationRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ONBOARDING_SECURITY_REQUIREMENTS.md test matrix (spec sections 84). */
@ExtendWith(MockitoExtension.class)
class UserOnboardingServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long INITIATOR_USER_ID = 100L;

    @Mock private ApplicationUserRepository userRepository;
    @Mock private UserInvitationRepository invitationRepository;
    @Mock private UserRoleAssignmentRepository assignmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmailService emailService;
    @Mock private AuditLogRecorder auditLogRecorder;

    private UserOnboardingService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new UserOnboardingService(userRepository, invitationRepository, assignmentRepository, employeeRepository,
                new InvitationTokenService(), emailService, auditLogRecorder, 24, "https://hrms.example.test");

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
        employee.setOfficialEmail("asha.rao@jcimail.in");
        lenient().when(userRepository.save(any(ApplicationUser.class))).thenAnswer(inv -> {
            ApplicationUser saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 200L);
            return saved;
        });
        lenient().when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(inv -> {
            UserInvitation saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 300L);
            return saved;
        });
    }

    // ---- previewCandidate ----

    @Test
    void previewCandidate_validJcimailEmail_isEligible() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(userRepository.findByEmployee_Id(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThat(service.previewCandidate(EMPLOYEE_ID)).isEqualTo(CandidateStatus.ELIGIBLE);
    }

    @Test
    void previewCandidate_missingOfficialEmail_isMissing() {
        employee.setOfficialEmail(null);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        assertThat(service.previewCandidate(EMPLOYEE_ID)).isEqualTo(CandidateStatus.OFFICIAL_EMAIL_MISSING);
    }

    @Test
    void previewCandidate_wrongDomain_isInvalidDomain() {
        employee.setOfficialEmail("asha.rao@gmail.com");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        assertThat(service.previewCandidate(EMPLOYEE_ID)).isEqualTo(CandidateStatus.INVALID_OFFICIAL_EMAIL_DOMAIN);
    }

    @Test
    void previewCandidate_alreadyActive_isAlreadyActive() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        ApplicationUser existing = new ApplicationUser(employee, "asha.rao@jcimail.in");
        existing.setStatus(UserAccountStatus.ACTIVE);
        when(userRepository.findByEmployee_Id(EMPLOYEE_ID)).thenReturn(Optional.of(existing));

        assertThat(service.previewCandidate(EMPLOYEE_ID)).isEqualTo(CandidateStatus.ALREADY_ACTIVE);
    }

    // ---- invite ----

    @Test
    void invite_eligibleEmployee_createsUserAndInvitationAndSendsEmail() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(userRepository.findByEmployee_Id(EMPLOYEE_ID)).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("asha.rao@jcimail.in")).thenReturn(false);

        UserInvitation invitation = service.invite(EMPLOYEE_ID, INITIATOR_USER_ID);

        assertThat(invitation).isNotNull();
        verify(emailService).send(eq("asha.rao@jcimail.in"), anyString(), anyString());
    }

    @Test
    void invite_ineligibleEmployee_throws() {
        employee.setOfficialEmail("asha.rao@gmail.com");
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.invite(EMPLOYEE_ID, INITIATOR_USER_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- resend ----

    @Test
    void resend_invalidatesPreviousInvitation_beforeIssuingNewOne() {
        ApplicationUser user = new ApplicationUser(employee, "asha.rao@jcimail.in");
        ReflectionTestUtils.setField(user, "id", 200L);
        user.setStatus(UserAccountStatus.INVITED);
        UserInvitation previous = new UserInvitation(user, INITIATOR_USER_ID, "old-hash", Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(previous, "id", 300L);
        when(invitationRepository.findById(300L)).thenReturn(Optional.of(previous));
        when(invitationRepository.findByUser_IdAndStatus(200L, InvitationStatus.INVITED)).thenReturn(List.of(previous));

        service.resend(300L, INITIATOR_USER_ID);

        assertThat(previous.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        verify(invitationRepository, times(1)).save(any(UserInvitation.class));
    }

    // ---- activate ----

    @Test
    void activate_validToken_marksAcceptedAndActivatesUser_andEnsuresBaselineRole() {
        ApplicationUser user = new ApplicationUser(employee, "asha.rao@jcimail.in");
        ReflectionTestUtils.setField(user, "id", 200L);
        InvitationTokenService tokenService = new InvitationTokenService();
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hash(rawToken);
        UserInvitation invitation = new UserInvitation(user, INITIATOR_USER_ID, hash, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(invitation, "id", 300L);
        when(invitationRepository.findByTokenHashAndStatus(hash, InvitationStatus.INVITED)).thenReturn(Optional.of(invitation));
        when(userRepository.findById(INITIATOR_USER_ID)).thenReturn(Optional.empty()); // initiator not resolvable - confirmation just skipped
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(eq(200L), anyString())).thenReturn(List.of());

        UserOnboardingService.ActivationOutcome outcome = service.activate(rawToken);

        assertThat(outcome).isEqualTo(UserOnboardingService.ActivationOutcome.SUCCESS);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(user.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    void activate_expiredToken_marksExpired_doesNotActivate() {
        ApplicationUser user = new ApplicationUser(employee, "asha.rao@jcimail.in");
        ReflectionTestUtils.setField(user, "id", 200L);
        InvitationTokenService tokenService = new InvitationTokenService();
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hash(rawToken);
        UserInvitation invitation = new UserInvitation(user, INITIATOR_USER_ID, hash, Instant.now().minusSeconds(3600));
        when(invitationRepository.findByTokenHashAndStatus(hash, InvitationStatus.INVITED)).thenReturn(Optional.of(invitation));

        UserOnboardingService.ActivationOutcome outcome = service.activate(rawToken);

        assertThat(outcome).isEqualTo(UserOnboardingService.ActivationOutcome.EXPIRED);
        assertThat(user.getStatus()).isNotEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    void activate_unknownToken_returnsInvalid_withoutRevealingWhetherAnyRecordExists() {
        when(invitationRepository.findByTokenHashAndStatus(anyString(), eq(InvitationStatus.INVITED))).thenReturn(Optional.empty());

        assertThat(service.activate("not-a-real-token")).isEqualTo(UserOnboardingService.ActivationOutcome.INVALID);
    }

    /** ONBOARDING_SECURITY_REQUIREMENTS.md §8/§12: activation must not roll back merely because the initiator confirmation email fails. */
    @Test
    void activate_whenConfirmationEmailFails_activationStillSucceeds() {
        ApplicationUser user = new ApplicationUser(employee, "asha.rao@jcimail.in");
        ReflectionTestUtils.setField(user, "id", 200L);
        InvitationTokenService tokenService = new InvitationTokenService();
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hash(rawToken);
        UserInvitation invitation = new UserInvitation(user, INITIATOR_USER_ID, hash, Instant.now().plusSeconds(3600));
        when(invitationRepository.findByTokenHashAndStatus(hash, InvitationStatus.INVITED)).thenReturn(Optional.of(invitation));
        when(assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(eq(200L), anyString())).thenReturn(List.of());
        ApplicationUser initiator = new ApplicationUser(employee, "initiator@jcimail.in");
        ReflectionTestUtils.setField(initiator, "id", INITIATOR_USER_ID);
        when(userRepository.findById(INITIATOR_USER_ID)).thenReturn(Optional.of(initiator));
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(emailService).send(anyString(), anyString(), anyString());

        UserOnboardingService.ActivationOutcome outcome = service.activate(rawToken);

        assertThat(outcome).isEqualTo(UserOnboardingService.ActivationOutcome.SUCCESS);
        assertThat(user.getStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    }
}
