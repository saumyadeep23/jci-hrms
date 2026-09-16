package in.gov.jci.hrms.security.onboarding;

import in.gov.jci.hrms.audit.AuditLogRecorder;
import in.gov.jci.hrms.entity.ApplicationUser;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.InvitationStatus;
import in.gov.jci.hrms.entity.ScopeType;
import in.gov.jci.hrms.entity.UserAccountStatus;
import in.gov.jci.hrms.entity.UserInvitation;
import in.gov.jci.hrms.entity.UserRoleAssignment;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.ApplicationUserRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.UserInvitationRepository;
import in.gov.jci.hrms.repository.UserRoleAssignmentRepository;
import in.gov.jci.hrms.security.ApplicationRole;
import in.gov.jci.hrms.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ONBOARDING_SECURITY_REQUIREMENTS.md end to end: eligibility preview, invite/resend/revoke,
 * public activation, initiator confirmation. Onboarding authority (invite/resend/revoke/bulk) is
 * enforced at the controller layer (RbacPermission.USER_INVITE*, restricted to SYSTEM_ADMIN/
 * HR_ADMIN by the V94 role_permissions seed) - this service trusts its caller has already been
 * authorized and focuses on the eligibility/token/lifecycle rules themselves.
 */
@Service
@Transactional(readOnly = true)
public class UserOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(UserOnboardingService.class);

    private final ApplicationUserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final InvitationTokenService tokenService;
    private final EmailService emailService;
    private final AuditLogRecorder auditLogRecorder;
    private final long validityHours;
    private final String frontendBaseUrl;

    public UserOnboardingService(ApplicationUserRepository userRepository, UserInvitationRepository invitationRepository,
                                  UserRoleAssignmentRepository assignmentRepository, EmployeeRepository employeeRepository,
                                  InvitationTokenService tokenService, EmailService emailService, AuditLogRecorder auditLogRecorder,
                                  @Value("${hrms.onboarding.invitation-validity-hours:24}") long validityHours,
                                  @Value("${hrms.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.assignmentRepository = assignmentRepository;
        this.employeeRepository = employeeRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.auditLogRecorder = auditLogRecorder;
        this.validityHours = validityHours;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public CandidateStatus previewCandidate(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        return classify(employee);
    }

    public List<CandidateStatus> previewBulk(List<Long> employeeIds) {
        return employeeIds.stream().map(this::previewCandidate).toList();
    }

    private CandidateStatus classify(Employee employee) {
        OfficialEmailValidator.Result emailResult = OfficialEmailValidator.validate(employee.getOfficialEmail());
        if (emailResult == OfficialEmailValidator.Result.MISSING) {
            return CandidateStatus.OFFICIAL_EMAIL_MISSING;
        }
        if (emailResult == OfficialEmailValidator.Result.MALFORMED) {
            return CandidateStatus.INVALID_OFFICIAL_EMAIL;
        }
        if (emailResult == OfficialEmailValidator.Result.WRONG_DOMAIN) {
            return CandidateStatus.INVALID_OFFICIAL_EMAIL_DOMAIN;
        }
        Optional<ApplicationUser> existing = userRepository.findByEmployee_Id(employee.getId());
        if (existing.isPresent()) {
            ApplicationUser user = existing.get();
            if (user.getStatus() == UserAccountStatus.ACTIVE) {
                return CandidateStatus.ALREADY_ACTIVE;
            }
            if (user.getStatus() == UserAccountStatus.INVITED
                    && !invitationRepository.findByUser_IdAndStatus(user.getId(), InvitationStatus.INVITED).isEmpty()) {
                return CandidateStatus.INVITATION_PENDING;
            }
            return CandidateStatus.ALREADY_PROVISIONED;
        }
        return CandidateStatus.ELIGIBLE;
    }

    /** initiatingUserId must be SYSTEM_ADMIN/HR_ADMIN - enforced by the controller's @PreAuthorize, not re-checked here. */
    @Transactional
    public UserInvitation invite(Long employeeId, Long initiatingUserId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        CandidateStatus status = classify(employee);
        if (status != CandidateStatus.ELIGIBLE) {
            throw new BusinessRuleViolationException("Employee " + employeeId + " is not invitation-eligible: " + status);
        }

        String username = OfficialEmailValidator.normalize(employee.getOfficialEmail());
        if (userRepository.existsByUsername(username)) {
            throw new BusinessRuleViolationException("Username " + username + " is already in use by another account - conflict, not auto-renamed");
        }
        ApplicationUser user = userRepository.save(new ApplicationUser(employee, username));
        UserInvitation invitation = issueInvitation(user, initiatingUserId);
        user.setStatus(UserAccountStatus.INVITED);

        auditLogRecorder.record("UserInvitation", invitation.getId(), AuditAction.CREATE, null,
                Map.of("event", "USER_INVITED", "employeeId", employeeId, "initiatedBy", initiatingUserId));
        return invitation;
    }

    /** Resend invalidates the previous active invitation atomically before issuing a new one (ONBOARDING_SECURITY_REQUIREMENTS.md §7). Takes the existing invitation's id (REST resource identity), not the user id. */
    @Transactional
    public UserInvitation resend(Long invitationId, Long initiatingUserId) {
        UserInvitation previous = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new MasterDataNotFoundException("User Invitation", invitationId));
        ApplicationUser user = previous.getUser();
        if (user.getStatus() != UserAccountStatus.INVITED && user.getStatus() != UserAccountStatus.PENDING_INVITATION) {
            throw new BusinessRuleViolationException("Application user " + user.getId() + " is not in an invitable state: " + user.getStatus());
        }
        for (UserInvitation pending : invitationRepository.findByUser_IdAndStatus(user.getId(), InvitationStatus.INVITED)) {
            pending.revoke(initiatingUserId);
        }
        UserInvitation invitation = issueInvitation(user, initiatingUserId);
        user.setStatus(UserAccountStatus.INVITED);

        auditLogRecorder.record("UserInvitation", invitation.getId(), AuditAction.CREATE, null,
                Map.of("event", "INVITATION_RESENT", "userId", user.getId(), "initiatedBy", initiatingUserId));
        return invitation;
    }

    @Transactional
    public void revoke(Long invitationId, Long actingUserId) {
        UserInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new MasterDataNotFoundException("User Invitation", invitationId));
        if (invitation.getStatus() != InvitationStatus.INVITED) {
            throw new BusinessRuleViolationException("Invitation " + invitationId + " is not active: " + invitation.getStatus());
        }
        invitation.revoke(actingUserId);
        auditLogRecorder.record("UserInvitation", invitationId, AuditAction.UPDATE, null,
                Map.of("event", "INVITATION_REVOKED", "revokedBy", actingUserId));
    }

    public enum ActivationOutcome {
        SUCCESS, INVALID, EXPIRED
    }

    /** Public, pre-auth. Never reveals whether a token was invalid vs. simply not found (ONBOARDING_SECURITY_REQUIREMENTS.md §5). */
    @Transactional
    public ActivationOutcome activate(String rawToken) {
        String hash = tokenService.hash(rawToken);
        Optional<UserInvitation> found = invitationRepository.findByTokenHashAndStatus(hash, InvitationStatus.INVITED);
        if (found.isEmpty()) {
            return ActivationOutcome.INVALID;
        }
        UserInvitation invitation = found.get();
        if (invitation.isExpired(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            return ActivationOutcome.EXPIRED;
        }
        // @Version on UserInvitation (Hibernate optimistic locking) means a concurrent
        // activate-vs-activate or activate-vs-resend race on this exact row throws
        // ObjectOptimisticLockingFailureException on commit for the loser - already mapped to 409
        // by GlobalExceptionHandler, not caught here (ONBOARDING_SECURITY_REQUIREMENTS.md §9/§6).
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        ApplicationUser user = invitation.getUser();
        user.setStatus(UserAccountStatus.ACTIVE);
        ensureBaselineUserRole(user);

        auditLogRecorder.record("UserInvitation", invitation.getId(), AuditAction.UPDATE, null,
                Map.of("event", "USER_ACTIVATED", "userId", user.getId()));

        sendActivationConfirmationBestEffort(invitation);
        return ActivationOutcome.SUCCESS;
    }

    private void ensureBaselineUserRole(ApplicationUser user) {
        if (assignmentRepository.findByUser_IdAndRoleCodeAndRevokedAtIsNull(user.getId(), ApplicationRole.USER).isEmpty()) {
            assignmentRepository.save(new UserRoleAssignment(user, ApplicationRole.USER, ScopeType.SELF, null, null));
        }
    }

    /** ONBOARDING_SECURITY_REQUIREMENTS.md §8/§12: activation succeeding must never roll back because this failed; failure is recorded, not retried against a personal-email fallback. */
    private void sendActivationConfirmationBestEffort(UserInvitation invitation) {
        try {
            Long initiatorUserId = invitation.getInitiatedByUserId();
            ApplicationUser initiator = userRepository.findById(initiatorUserId).orElse(null);
            if (initiator == null) {
                log.warn("Activation confirmation skipped: initiator user {} no longer resolvable", initiatorUserId);
                return;
            }
            String initiatorEmail = initiator.getEmployee().getOfficialEmail();
            if (OfficialEmailValidator.validate(initiatorEmail) != OfficialEmailValidator.Result.VALID) {
                log.warn("Activation confirmation skipped: initiator {} has no valid official email", initiatorUserId);
                return;
            }
            emailService.send(initiatorEmail, "HRMS account activated",
                    "The HRMS account you provisioned for employee " + invitation.getUser().getEmployee().getId()
                            + " was activated at " + invitation.getAcceptedAt() + ".");
        } catch (RuntimeException e) {
            log.warn("Activation confirmation email failed for invitation {}", invitation.getId(), e);
            auditLogRecorder.record("UserInvitation", invitation.getId(), AuditAction.UPDATE, null,
                    Map.of("event", "ACTIVATION_CONFIRMATION_FAILED", "timestamp", Instant.now().toString()));
        }
    }

    private UserInvitation issueInvitation(ApplicationUser user, Long initiatingUserId) {
        String rawToken = tokenService.generateRawToken();
        String hash = tokenService.hash(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(validityHours * 3600);
        UserInvitation invitation = invitationRepository.save(new UserInvitation(user, initiatingUserId, hash, expiresAt));

        String activationLink = frontendBaseUrl + "/activate?token=" + rawToken;
        emailService.send(user.getUsername(), "JCI HRMS account activation",
                "Activate your HRMS account within " + validityHours + " hours: " + activationLink);
        return invitation;
    }
}
