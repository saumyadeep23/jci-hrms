package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CeaClaimBillPassRequest;
import in.gov.jci.hrms.dto.CeaClaimRejectRequest;
import in.gov.jci.hrms.dto.CeaClaimResponse;
import in.gov.jci.hrms.dto.CeaClaimSubmitRequest;
import in.gov.jci.hrms.dto.CeaClaimVerifyRequest;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CeaClaimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CEA/Hostel Subsidy claim lifecycle: employee self-service submit -&gt; HR verify -&gt; Finance
 * bill-pass, then PayrollBatchComputationService picks up BILL_PASSED claims on its own next run
 * (see CeaClaimService's own javadoc). Paths mirror LeaveEncashmentController's real, established
 * self-service/admin split (`/api/v1/self-service/...` + `/api/v1/admin/...`) rather than a
 * `/api/v1/payroll/cea-claims` base path, which has no precedent anywhere in this codebase - CEA's
 * lifecycle (employee application -&gt; HR gate -&gt; Finance gate -&gt; payroll pickup) is structurally the
 * same shape as EL encashment's, which uses exactly this split despite also feeding into the payroll
 * engine.
 */
@RestController
public class CeaClaimController {

    private final CeaClaimService ceaClaimService;

    public CeaClaimController(CeaClaimService ceaClaimService) {
        this.ceaClaimService = ceaClaimService;
    }

    @PostMapping("/api/v1/self-service/cea-claims")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CeaClaimResponse> submitClaim(@Valid @RequestBody CeaClaimSubmitRequest request, Authentication authentication) {
        Long employeeId = requireCurrentEmployeeId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(ceaClaimService.submitClaim(employeeId, request));
    }

    @GetMapping("/api/v1/self-service/cea-claims/my-claims")
    @PreAuthorize("isAuthenticated()")
    public List<CeaClaimResponse> myClaims(Authentication authentication) {
        Long employeeId = requireCurrentEmployeeId(authentication);
        return ceaClaimService.listByEmployee(employeeId);
    }

    @GetMapping("/api/v1/admin/cea-claims/pending-verification")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<CeaClaimResponse> pendingVerification() {
        return ceaClaimService.pendingVerification();
    }

    @PutMapping("/api/v1/admin/cea-claims/{id}/verify")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public CeaClaimResponse verify(@PathVariable Long id, @Valid @RequestBody CeaClaimVerifyRequest request, Authentication authentication) {
        return ceaClaimService.verifyClaim(id, requireCurrentEmployeeId(authentication), request);
    }

    @GetMapping("/api/v1/admin/cea-claims/pending-bill-passing")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public List<CeaClaimResponse> pendingBillPassing() {
        return ceaClaimService.pendingBillPassing();
    }

    /** HR/Finance History &amp; Log view - every claim regardless of status, both gates can see the full audit trail. */
    @GetMapping("/api/v1/admin/cea-claims")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public List<CeaClaimResponse> listAll() {
        return ceaClaimService.listAll();
    }

    @PutMapping("/api/v1/admin/cea-claims/{id}/pass-bill")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public CeaClaimResponse passBill(@PathVariable Long id, @Valid @RequestBody CeaClaimBillPassRequest request, Authentication authentication) {
        return ceaClaimService.passBill(id, requireCurrentEmployeeId(authentication), request);
    }

    /** Either gate (HR at SUBMITTED, Finance at VERIFIED) may reject - CeaClaimService.rejectClaim() itself enforces which statuses are actually rejectable. */
    @PutMapping("/api/v1/admin/cea-claims/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public CeaClaimResponse reject(@PathVariable Long id, @Valid @RequestBody CeaClaimRejectRequest request) {
        return ceaClaimService.rejectClaim(id, request.reason());
    }

    private Long requireCurrentEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve whose CEA claim this is");
        }
        return employeeId;
    }
}
