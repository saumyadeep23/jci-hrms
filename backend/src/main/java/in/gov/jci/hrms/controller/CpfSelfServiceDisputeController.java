package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfDisputeClarificationResponseRequest;
import in.gov.jci.hrms.dto.CpfDisputeCreateRequest;
import in.gov.jci.hrms.dto.CpfDisputeResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfTransactionDisputeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * CPF Transaction Dispute self-service (Part 8/9). Same IDOR-safe pattern as
 * CpfSelfServicePassbookController - employeeId always comes from the JWT, never a request parameter.
 * Raising, viewing, withdrawing, and responding to a clarification are the only actions exposed here;
 * assign/start-review/request-clarification/resolve/reject live on CpfDisputeAdminController, gated to
 * CPF_ADMIN/SUPER_ADMIN - an ordinary employee can never resolve or reject their own dispute (Part 20).
 */
@RestController
@RequestMapping("/api/v1/ess/cpf/disputes")
@PreAuthorize("isAuthenticated()")
public class CpfSelfServiceDisputeController {

    private final CpfTransactionDisputeService disputeService;

    public CpfSelfServiceDisputeController(CpfTransactionDisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping
    public ResponseEntity<CpfDisputeResponse> raise(@Valid @RequestBody CpfDisputeCreateRequest request, Authentication authentication) {
        CpfDisputeResponse created = disputeService.raiseDispute(requireCurrentEmployeeId(authentication), request);
        return ResponseEntity.created(URI.create("/api/v1/ess/cpf/disputes/" + created.id())).body(created);
    }

    @GetMapping
    public List<CpfDisputeResponse> myDisputes(Authentication authentication) {
        return disputeService.myDisputes(requireCurrentEmployeeId(authentication));
    }

    @GetMapping("/{id}")
    public CpfDisputeResponse getMine(@PathVariable Long id, Authentication authentication) {
        return disputeService.getMyDispute(requireCurrentEmployeeId(authentication), id);
    }

    @PostMapping("/{id}/withdraw")
    public CpfDisputeResponse withdraw(@PathVariable Long id, Authentication authentication) {
        return disputeService.withdraw(requireCurrentEmployeeId(authentication), id);
    }

    @PostMapping("/{id}/respond")
    public CpfDisputeResponse respond(@PathVariable Long id, @Valid @RequestBody CpfDisputeClarificationResponseRequest request,
                                       Authentication authentication) {
        return disputeService.respondToClarification(requireCurrentEmployeeId(authentication), id, request.response());
    }

    private Long requireCurrentEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Authenticated token has no employee_id claim");
        }
        return employeeId;
    }
}
