package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.AttendanceRegularizationRequest;
import in.gov.jci.hrms.dto.AttendanceRegularizationResponse;
import in.gov.jci.hrms.dto.RegularizationDecisionRequest;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.AttendanceRegularizationApplicationRepository;
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

    public AttendanceRegularizationController(AttendanceRegularizationService attendanceRegularizationService,
                                                AttendanceRegularizationApplicationRepository regularizationRepository) {
        this.attendanceRegularizationService = attendanceRegularizationService;
        this.regularizationRepository = regularizationRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AttendanceRegularizationResponse> submit(@Valid @RequestBody AttendanceRegularizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attendanceRegularizationService.submit(request));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public AttendanceRegularizationResponse approve(@PathVariable Long id, @Valid @RequestBody RegularizationDecisionRequest decision) {
        return attendanceRegularizationService.approve(id, decision);
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
}
