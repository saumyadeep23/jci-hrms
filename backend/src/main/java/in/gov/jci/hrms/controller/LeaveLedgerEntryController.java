package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveLedgerEntryResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.LeaveLedgerEntryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only audit trail of every auto-debit AttendanceLeaveDeductionService has made. */
@RestController
@RequestMapping("/api/leave-ledger-entries")
public class LeaveLedgerEntryController {

    private final LeaveLedgerEntryService leaveLedgerEntryService;

    public LeaveLedgerEntryController(LeaveLedgerEntryService leaveLedgerEntryService) {
        this.leaveLedgerEntryService = leaveLedgerEntryService;
    }

    /**
     * FINANCE_ADMIN is added on top of attendanceAggSec's own self-or-HR_ADMIN/SUPER_ADMIN rule
     * (not folded into that shared bean, which other, non-leave endpoints also use) so a Finance
     * (Gate 2) reviewer can pull up an employee's EL ledger from the encashment admin review queue.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated() and (@attendanceAggSec.canEvaluateFor(authentication, #employeeId) or hasAnyRole('FINANCE_ADMIN'))")
    public List<LeaveLedgerEntryResponse> list(@RequestParam(required = false) Long employeeId,
                                                @RequestParam(required = false) String leaveTypeCode,
                                                Authentication authentication) {
        Long target = employeeId != null ? employeeId : selfOrThrow(authentication);
        List<LeaveLedgerEntryResponse> entries = leaveLedgerEntryService.listForEmployee(target);
        if (leaveTypeCode != null && !leaveTypeCode.isBlank()) {
            entries = entries.stream().filter(e -> leaveTypeCode.equalsIgnoreCase(e.leaveTypeCode())).toList();
        }
        return entries;
    }

    private Long selfOrThrow(Authentication authentication) {
        Long selfId = SecurityUtils.currentEmployeeId(authentication);
        if (selfId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve whose leave ledger to return");
        }
        return selfId;
    }
}
