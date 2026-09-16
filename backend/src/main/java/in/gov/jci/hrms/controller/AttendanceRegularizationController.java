package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AttendanceRegularizationRequest;
import in.gov.jci.hrms.dto.AttendanceRegularizationResponse;
import in.gov.jci.hrms.dto.RegularizationDecisionRequest;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.AttendanceRegularizationApplicationRepository;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.AttendanceRegularizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** PIMS ALMS Phase 2, Section 5 - HoD approval workflow for a REQUIRES_REGULARIZATION/UNAUTHORIZED_LATE day. GET endpoints (mine/pending-approval) were added in Phase 3 for the frontend history and HoD queue views. */
@RestController
@RequestMapping("/api/v1/attendance/regularization")
public class AttendanceRegularizationController {

    private final AttendanceRegularizationService attendanceRegularizationService;
    private final AttendanceRegularizationApplicationRepository regularizationRepository;
    private final AttendanceAggregationSecurity attendanceAggSec;

    public AttendanceRegularizationController(AttendanceRegularizationService attendanceRegularizationService,
                                                AttendanceRegularizationApplicationRepository regularizationRepository,
                                                AttendanceAggregationSecurity attendanceAggSec) {
        this.attendanceRegularizationService = attendanceRegularizationService;
        this.regularizationRepository = regularizationRepository;
        this.attendanceAggSec = attendanceAggSec;
    }

    /** SEC-005 remediation (docs/security/SEC_001_002_REMEDIATION.md pattern): reuses @attendanceAggSec exactly like MobilePunchController's SEC-002 fix, rather than inventing a second ownership mechanism. */
    @PostMapping
    @PreAuthorize("@attendanceAggSec.canEvaluateFor(authentication, #request.employeeId())")
    public ResponseEntity<AttendanceRegularizationResponse> submit(@Valid @RequestBody AttendanceRegularizationRequest request,
                                                                     Authentication authentication) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        boolean onBehalfOfOthersPermitted = attendanceAggSec.canActOnBehalfOfOthers(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceRegularizationService.submit(request, callerEmployeeId, onBehalfOfOthersPermitted));
    }

    /**
     * Widened from HR_ADMIN/SUPER_ADMIN-only: the designated approver resolved at submission time
     * (see SupervisorResolutionService) may be a plain EMPLOYEE-role manager, not necessarily an
     * HR_ADMIN - the GET .../pending-approval queue below already grants any authenticated employee
     * their own queue, so this write path now matches it instead of 403ing every non-HR_ADMIN who
     * views a queue they can't act on. There is no HR_ADMIN/SUPER_ADMIN override here though: an admin
     * can see every request (see GET /all below) but acting on one still requires being the actual
     * designated approver - the service enforces that.
     */
    @PatchMapping("/{id}/approve")
    @PreAuthorize("isAuthenticated()")
    public AttendanceRegularizationResponse approve(@PathVariable Long id, @Valid @RequestBody RegularizationDecisionRequest decision,
                                                      Authentication authentication) {
        Long callerId = SecurityUtils.currentEmployeeId(authentication);
        if (callerId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve who is deciding this application");
        }
        return attendanceRegularizationService.approve(id, decision, callerId);
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<AttendanceRegularizationResponse> mine(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve whose regularization requests to return");
        }
        return regularizationRepository.findByEmployeeId(employeeId).stream().map(AttendanceRegularizationResponse::from).toList();
    }

    /** HoD queue: pending requests this authenticated employee was resolved as the designated approver for at submission time. */
    @GetMapping("/pending-approval")
    @PreAuthorize("isAuthenticated()")
    public List<AttendanceRegularizationResponse> pendingApproval(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve your approval queue");
        }
        return regularizationRepository.findByDesignatedApproverIdAndApprovalStatus(employeeId, ApprovalStatus.PENDING).stream()
                .map(AttendanceRegularizationResponse::from)
                .toList();
    }

    /** Every request ever routed to this approver, any status - backs RegularizationApprovalQueuePage's metric cards and Pending/Approved/Rejected status filter. */
    @GetMapping("/mine-as-approver")
    @PreAuthorize("isAuthenticated()")
    public List<AttendanceRegularizationResponse> mineAsApprover(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve your approval history");
        }
        return attendanceRegularizationService.findAllDecidedByApprover(employeeId);
    }

    /** HR_ADMIN/SUPER_ADMIN organization-wide visibility - every regularization request, any status, regardless of designated approver. View-only: PATCH .../approve still requires being the actual designated approver on that request. */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public List<AttendanceRegularizationResponse> all() {
        return attendanceRegularizationService.findAll();
    }
}
