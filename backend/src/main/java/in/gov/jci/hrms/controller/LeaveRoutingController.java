package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveApplicationResponse;
import in.gov.jci.hrms.dto.LeaveDecisionRequest;
import in.gov.jci.hrms.dto.LeaveForwardRequest;
import in.gov.jci.hrms.dto.LeaveRoutingActionResponse;
import in.gov.jci.hrms.dto.LeaveSanctionHistoryResponse;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.LeaveApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Multi-tier routing/forwarding/sanctioning surface layered on top of the existing single-tier
 * /api/leave-applications approve|reject (LeaveApplicationController), which keeps working
 * unchanged. sanction()/rejectWithRemarks() here delegate to the same balance-debit / reservation-
 * release logic as the old approve()/reject(), so a leave decided through either surface behaves
 * identically from the ledger's point of view.
 */
@RestController
@RequestMapping("/api/v1/leaves")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN')")
public class LeaveRoutingController {

    private final LeaveApplicationService leaveApplicationService;

    public LeaveRoutingController(LeaveApplicationService leaveApplicationService) {
        this.leaveApplicationService = leaveApplicationService;
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isCurrentAssignee(authentication, #id)")
    public LeaveApplicationResponse forward(@PathVariable Long id, @Valid @RequestBody LeaveForwardRequest request,
                                             Authentication authentication) {
        return leaveApplicationService.forward(id, request.forwardedToEmployeeId(), request.remarks(),
                SecurityUtils.currentEmployeeId(authentication));
    }

    @PostMapping("/{id}/sanction")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isCurrentAssignee(authentication, #id)")
    public LeaveApplicationResponse sanction(@PathVariable Long id, @RequestBody(required = false) LeaveDecisionRequest request,
                                              Authentication authentication) {
        String remarks = request != null ? request.remarks() : null;
        return leaveApplicationService.sanction(id, remarks, SecurityUtils.currentEmployeeId(authentication));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isCurrentAssignee(authentication, #id)")
    public LeaveApplicationResponse reject(@PathVariable Long id, @RequestBody LeaveDecisionRequest request,
                                            Authentication authentication) {
        return leaveApplicationService.rejectWithRemarks(id, request.remarks(), SecurityUtils.currentEmployeeId(authentication));
    }

    @GetMapping("/{id}/routing-history")
    @PreAuthorize("hasRole('HR_ADMIN') or @leaveSec.isSelf(authentication, #id) "
            + "or @leaveSec.isApprover(authentication, #id) or @leaveSec.isCurrentAssignee(authentication, #id)")
    public List<LeaveRoutingActionResponse> routingHistory(@PathVariable Long id) {
        return leaveApplicationService.getRoutingHistory(id);
    }

    @GetMapping("/sanctions/history")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<LeaveSanctionHistoryResponse> sanctionsHistory(@RequestParam int year,
                                                                @RequestParam(required = false) Integer month,
                                                                @RequestParam(required = false) String status) {
        return leaveApplicationService.getSanctionsHistory(year, month, status);
    }
}
