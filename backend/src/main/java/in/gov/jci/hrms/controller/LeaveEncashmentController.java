package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EncashmentGateDecisionRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentHistoryResponse;
import in.gov.jci.hrms.dto.LeaveEncashmentRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.LeaveEncashmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;

/** PIMS ALMS Phase 2, Section 4 - two-gate (HR then Finance) in-service EL encashment workflow. GET endpoints (list/mine) were added in Phase 3 so the frontend history/queue views have something to read. */
@RestController
public class LeaveEncashmentController {

    private final LeaveEncashmentService leaveEncashmentService;
    private final AttendanceAggregationSecurity attendanceAggSec;

    public LeaveEncashmentController(LeaveEncashmentService leaveEncashmentService, AttendanceAggregationSecurity attendanceAggSec) {
        this.leaveEncashmentService = leaveEncashmentService;
        this.attendanceAggSec = attendanceAggSec;
    }

    /** SEC-006 remediation (docs/security/SEC_001_002_REMEDIATION.md pattern). */
    @PostMapping("/api/v1/self-service/leave/encashment")
    @PreAuthorize("@attendanceAggSec.canEvaluateFor(authentication, #request.employeeId())")
    public ResponseEntity<LeaveEncashmentResponse> apply(@Valid @RequestBody LeaveEncashmentRequest request, Authentication authentication) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        boolean onBehalfOfOthersPermitted = attendanceAggSec.canActOnBehalfOfOthers(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leaveEncashmentService.apply(request, callerEmployeeId, onBehalfOfOthersPermitted));
    }

    @GetMapping("/api/v1/self-service/leave/encashment/mine")
    @PreAuthorize("isAuthenticated()")
    public List<LeaveEncashmentResponse> mine(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve whose encashment applications to return");
        }
        return leaveEncashmentService.listByEmployee(employeeId);
    }

    @GetMapping("/api/v1/admin/leave/encashment")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public List<LeaveEncashmentResponse> list() {
        return leaveEncashmentService.listForAdminReview();
    }

    /** Every encashment application for one employee, any gate status - the EL ledger modal's "under process" hold rows. */
    @GetMapping("/api/v1/admin/leave/encashment/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public List<LeaveEncashmentResponse> byEmployee(@PathVariable Long employeeId) {
        return leaveEncashmentService.listByEmployee(employeeId);
    }

    /** Month/year-wise sanction history (HR/Finance audit) - month null or 0 returns the whole year. */
    @GetMapping("/api/v1/admin/leave/encashment/history")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public List<LeaveEncashmentHistoryResponse> history(@RequestParam(required = false) Integer year,
                                                          @RequestParam(required = false) Integer month) {
        int resolvedYear = year != null ? year : Year.now().getValue();
        return leaveEncashmentService.listHistory(resolvedYear, month);
    }

    @PatchMapping("/api/v1/admin/leave/encashment/{id}/hr-approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public LeaveEncashmentResponse hrApprove(@PathVariable Long id, @Valid @RequestBody EncashmentGateDecisionRequest decision,
                                              Authentication authentication) {
        return leaveEncashmentService.hrApprove(id, decision, SecurityUtils.currentEmployeeId(authentication));
    }

    @PatchMapping("/api/v1/admin/leave/encashment/{id}/finance-approve")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'SUPER_ADMIN')")
    public LeaveEncashmentResponse financeApprove(@PathVariable Long id, @Valid @RequestBody EncashmentGateDecisionRequest decision,
                                                   Authentication authentication) {
        return leaveEncashmentService.financeApprove(id, decision, SecurityUtils.currentEmployeeId(authentication));
    }
}
