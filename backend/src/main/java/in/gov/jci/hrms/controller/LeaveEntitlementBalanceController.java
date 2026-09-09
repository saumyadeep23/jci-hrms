package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LeaveEntitlementBalanceResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.List;

/** Read-only self-service view of the caller's own EL entitlement sub-ledger (ALMS Phase 3's "Split Balance Display Card"). */
@RestController
@RequestMapping("/api/v1/leave-entitlement-balance")
public class LeaveEntitlementBalanceController {

    private final LeaveEntitlementBalanceRepository entitlementBalanceRepository;

    public LeaveEntitlementBalanceController(LeaveEntitlementBalanceRepository entitlementBalanceRepository) {
        this.entitlementBalanceRepository = entitlementBalanceRepository;
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<LeaveEntitlementBalanceResponse> mine(@RequestParam(required = false) Integer year, Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve whose entitlement balance to return");
        }
        int resolvedYear = year != null ? year : Year.now().getValue();
        return entitlementBalanceRepository.findByEmployeeIdAndYear(employeeId, resolvedYear).stream()
                .map(LeaveEntitlementBalanceResponse::from)
                .toList();
    }

    /** Admin counterpart of mine() - e.g. the EL ledger audit modal in the encashment admin review queue needs another employee's balance summary, not just the caller's own. */
    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public List<LeaveEntitlementBalanceResponse> byEmployee(@PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        int resolvedYear = year != null ? year : Year.now().getValue();
        return entitlementBalanceRepository.findByEmployeeIdAndYear(employeeId, resolvedYear).stream()
                .map(LeaveEntitlementBalanceResponse::from)
                .toList();
    }
}
