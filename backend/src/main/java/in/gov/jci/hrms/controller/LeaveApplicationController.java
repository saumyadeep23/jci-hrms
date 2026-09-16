package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveApplicationPreviewRequest;
import in.gov.jci.hrms.dto.LeaveApplicationPreviewResponse;
import in.gov.jci.hrms.dto.LeaveApplicationRequest;
import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.LeaveApplicationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/leave-applications")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN')")
public class LeaveApplicationController {

    private final LeaveApplicationService leaveApplicationService;
    private final AttendanceAggregationSecurity attendanceAggSec;

    public LeaveApplicationController(LeaveApplicationService leaveApplicationService, AttendanceAggregationSecurity attendanceAggSec) {
        this.leaveApplicationService = leaveApplicationService;
        this.attendanceAggSec = attendanceAggSec;
    }

    /** SEC-007 remediation (docs/security/SEC_001_002_REMEDIATION.md pattern) - overrides the class-level role-only check with an ownership-aware one. */
    @PostMapping
    @PreAuthorize("@attendanceAggSec.canEvaluateFor(authentication, #request.employeeId())")
    public ResponseEntity<LeaveApplicationResponse> create(@Valid @RequestBody LeaveApplicationRequest request, Authentication authentication) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        boolean onBehalfOfOthersPermitted = attendanceAggSec.canActOnBehalfOfOthers(authentication);
        LeaveApplicationResponse created = leaveApplicationService.create(request, callerEmployeeId, onBehalfOfOthersPermitted);
        return ResponseEntity.created(URI.create("/api/leave-applications/" + created.id())).body(created);
    }

    /** Dry-run of the same CCS validation create() applies - lets the frontend show calendar-days vs. debitable-days and surface rule violations before the employee submits. */
    @PostMapping("/preview")
    public LeaveApplicationPreviewResponse preview(@Valid @RequestBody LeaveApplicationPreviewRequest request) {
        return leaveApplicationService.preview(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isSelf(authentication, #id) or @leaveSec.isApprover(authentication, #id)")
    public LeaveApplicationResponse getById(@PathVariable Long id) {
        return leaveApplicationService.getById(id);
    }

    /** Edits a DRAFT in place - LeaveApplicationService.update() enforces the DRAFT-only status guard. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isSelf(authentication, #id)")
    public LeaveApplicationResponse update(@PathVariable Long id, @Valid @RequestBody LeaveApplicationRequest request) {
        return leaveApplicationService.update(id, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Page<LeaveApplicationResponse> list(Pageable pageable) {
        return leaveApplicationService.list(pageable);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isSelf(authentication, #id)")
    public LeaveApplicationResponse submit(@PathVariable Long id) {
        return leaveApplicationService.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isApprover(authentication, #id)")
    public LeaveApplicationResponse approve(@PathVariable Long id) {
        return leaveApplicationService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isApprover(authentication, #id)")
    public LeaveApplicationResponse reject(@PathVariable Long id) {
        return leaveApplicationService.reject(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isSelf(authentication, #id)")
    public LeaveApplicationResponse cancel(@PathVariable Long id) {
        return leaveApplicationService.cancel(id);
    }
}
