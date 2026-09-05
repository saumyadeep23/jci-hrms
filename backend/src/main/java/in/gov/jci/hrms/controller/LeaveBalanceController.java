package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveBalanceResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.LeaveBalanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Always the caller's own balances - employeeId is resolved from the JWT, mirroring AttendanceHistoryController's /my-history. */
@RestController
@RequestMapping("/api/leave-balances")
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalanceService;

    public LeaveBalanceController(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<LeaveBalanceResponse> mine(@RequestParam int year, Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException(
                    "Your token has no employee_id claim - cannot resolve whose leave balances to return");
        }
        return leaveBalanceService.listForEmployee(employeeId, year);
    }
}
