package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsFinancialPositionResponse;
import in.gov.jci.hrms.dto.JciEccsMemberResponse;
import in.gov.jci.hrms.dto.JciEccsMemberStatusChangeRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsMemberService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** JCIECCS membership reads (spec section 5) - same COOP_ADMIN/FINANCE_ADMIN/SUPER_ADMIN convention as
 * the rest of this module, plus self-access for financial-position (mirrors LoanController's
 * @loanSec.isSelf pattern) since a member should be able to see their own position. */
@RestController
@RequestMapping("/api/jcieccs/members")
public class JciEccsMemberController {

    private final JciEccsMemberService memberService;

    public JciEccsMemberController(JciEccsMemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public List<JciEccsMemberResponse> members() {
        return memberService.listMembers();
    }

    @GetMapping("/{employeeId}/financial-position")
    @PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN') or @jciEccsSec.isSelf(authentication, #employeeId)")
    public JciEccsFinancialPositionResponse financialPosition(@PathVariable Long employeeId) {
        return memberService.financialPosition(employeeId);
    }

    /** Membership status is a governance action (Bye-laws 15/16 suspension/cessation), scoped narrower
     * than the plain read endpoints above - COOP_ADMIN only, no FINANCE_ADMIN. Non-financial RBAC
     * migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed per confirmed business
     * direction ("SYSTEM_ADMIN must not independently suspend/cease JCIECCS membership - keep this
     * under JCIECCS functional administration"); the new DB-backed JCIECCS_APPROVE permission
     * (JCIECCS_ADMIN role) is added as the forward-looking path, alongside the legacy COOP_ADMIN JWT
     * role kept as compatibility since no real UserRoleAssignment data exists yet to grant JCIECCS_ADMIN
     * to anyone (application_users is empty outside the SYSTEM_ADMIN bootstrap - see
     * RBAC_MIGRATION_REPORT.md's "Migration of existing users" note). */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('COOP_ADMIN') or @rbac.hasPermission(authentication, 'JCIECCS_APPROVE')")
    public JciEccsMemberResponse changeStatus(@PathVariable Long id, @Valid @RequestBody JciEccsMemberStatusChangeRequest request,
                                               Authentication authentication) {
        return memberService.changeStatus(id, request, SecurityUtils.currentEmployeeId(authentication));
    }
}
