package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfRuleSimulatorRequest;
import in.gov.jci.hrms.dto.CpfRuleSimulatorResponse;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionRequest;
import in.gov.jci.hrms.dto.CpfWithdrawalRuleVersionResponse;
import in.gov.jci.hrms.dto.RejectRemarksRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfRuleSimulatorService;
import in.gov.jci.hrms.service.CpfWithdrawalRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * CPF Withdrawal Rule configuration - the DRAFT -&gt; PENDING_VERIFICATION -&gt; PENDING_APPROVAL -&gt;
 * APPROVED workflow (Parts 1-4) plus the Rule Simulator (Part 24). Same three-role convention as
 * CpfInterestRunController: reads open to FINANCE_ADMIN/CPF_ADMIN/SUPER_ADMIN, every state-changing action
 * narrowed to CPF_ADMIN/SUPER_ADMIN - this app has no separate Administrator/Secretariat/Approver role
 * today (see SecurityConfig), so create/verify/approve all share that same gate rather than inventing a
 * parallel security mechanism (Part 28).
 */
@RestController
@RequestMapping("/api/v1/payroll/trust/withdrawal-rules")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfWithdrawalRuleController {

    private final CpfWithdrawalRuleService ruleService;
    private final CpfRuleSimulatorService simulatorService;

    public CpfWithdrawalRuleController(CpfWithdrawalRuleService ruleService, CpfRuleSimulatorService simulatorService) {
        this.ruleService = ruleService;
        this.simulatorService = simulatorService;
    }

    @GetMapping
    public List<CpfWithdrawalRuleVersionResponse> listAll() {
        return ruleService.listAll();
    }

    @GetMapping("/purpose/{purposeCode}")
    public List<CpfWithdrawalRuleVersionResponse> listVersions(@PathVariable String purposeCode) {
        return ruleService.listVersions(purposeCode);
    }

    @GetMapping("/pending-approval")
    public List<CpfWithdrawalRuleVersionResponse> pendingApproval() {
        return ruleService.listPendingApproval();
    }

    @GetMapping("/{versionId}")
    public CpfWithdrawalRuleVersionResponse getVersion(@PathVariable UUID versionId) {
        return ruleService.getVersion(versionId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CpfWithdrawalRuleVersionResponse> createDraft(@Valid @RequestBody CpfWithdrawalRuleVersionRequest request,
                                                                          Authentication authentication) {
        String createdBy = SecurityUtils.currentUsername(authentication);
        CpfWithdrawalRuleVersionResponse created = ruleService.createDraft(request, createdBy != null ? createdBy : "system");
        return ResponseEntity.created(URI.create("/api/v1/payroll/trust/withdrawal-rules/" + created.id())).body(created);
    }

    @PostMapping("/{versionId}/submit")
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public CpfWithdrawalRuleVersionResponse submit(@PathVariable UUID versionId, Authentication authentication) {
        return ruleService.submitForVerification(versionId, actor(authentication));
    }

    @PostMapping("/{versionId}/verify")
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public CpfWithdrawalRuleVersionResponse verify(@PathVariable UUID versionId, Authentication authentication) {
        return ruleService.verify(versionId, actor(authentication));
    }

    @PostMapping("/{versionId}/approve")
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public CpfWithdrawalRuleVersionResponse approve(@PathVariable UUID versionId, @RequestParam(required = false) String approvalReference,
                                                     @Valid @RequestBody RejectRemarksRequest request, Authentication authentication) {
        return ruleService.approve(versionId, actor(authentication), approvalReference, request.remarks());
    }

    @PostMapping("/{versionId}/reject")
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public CpfWithdrawalRuleVersionResponse reject(@PathVariable UUID versionId, @Valid @RequestBody RejectRemarksRequest request,
                                                    Authentication authentication) {
        return ruleService.reject(versionId, actor(authentication), request.remarks());
    }

    @PostMapping("/simulate")
    public CpfRuleSimulatorResponse simulate(@Valid @RequestBody CpfRuleSimulatorRequest request) {
        return simulatorService.simulate(request);
    }

    private String actor(Authentication authentication) {
        String username = SecurityUtils.currentUsername(authentication);
        return username != null ? username : "system";
    }
}
