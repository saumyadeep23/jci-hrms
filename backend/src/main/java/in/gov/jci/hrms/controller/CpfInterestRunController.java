package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.dto.CpfInterestCalculateRequest;
import in.gov.jci.hrms.dto.CpfInterestCalculationPreviewResponse;
import in.gov.jci.hrms.dto.CpfInterestFinancialYearStatusResponse;
import in.gov.jci.hrms.dto.CpfInterestMemberBreakdown;
import in.gov.jci.hrms.dto.CpfInterestPostRequest;
import in.gov.jci.hrms.dto.CpfInterestReverseRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfInterestRunService;
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
 * CPF Interest Management module (Calculate -&gt; Approve/Post -&gt; Reverse -&gt; Recalculate) - see
 * CpfInterestRunService's own javadoc for the workflow. Same three-role convention this app already uses
 * for CPF Trust endpoints generally (CpfTrustController, CpfStatutoryInterestRateController): reads are
 * open to FINANCE_ADMIN/CPF_ADMIN/SUPER_ADMIN, every state-changing action (calculate, post, reverse) is
 * narrowed to CPF_ADMIN/SUPER_ADMIN. This app has no separate "Calculator" vs "Approver" role today (see
 * SecurityConfig) - approve-and-post is therefore one combined action, gated the same way calculate is.
 */
@RestController
@RequestMapping("/api/v1/payroll/trust/interest/runs")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfInterestRunController {

    private final CpfInterestRunService interestRunService;

    public CpfInterestRunController(CpfInterestRunService interestRunService) {
        this.interestRunService = interestRunService;
    }

    @GetMapping("/years")
    public List<CpfInterestFinancialYearStatusResponse> financialYearStatuses() {
        return interestRunService.listFinancialYearStatuses();
    }

    @GetMapping
    public List<CpfAnnualInterestRunResponse> listRuns() {
        return interestRunService.listRuns();
    }

    @GetMapping("/{runId}")
    public CpfAnnualInterestRunResponse getRun(@PathVariable Long runId) {
        return interestRunService.getRun(runId);
    }

    @GetMapping("/{runId}/members")
    public List<CpfInterestMemberBreakdown> runMemberBreakdown(@PathVariable Long runId) {
        return interestRunService.previewMembersForRun(runId);
    }

    @GetMapping("/member/{employeeId}")
    public List<CpfAnnualInterestRunResponse> memberRunHistory(@PathVariable Long employeeId) {
        return interestRunService.memberRunHistory(employeeId);
    }

    @PostMapping("/calculate")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public ResponseEntity<CpfInterestCalculationPreviewResponse> calculate(@Valid @RequestBody CpfInterestCalculateRequest request,
                                                                            Authentication authentication) {
        Long officerId = SecurityUtils.currentEmployeeId(authentication);
        CpfInterestCalculationPreviewResponse preview = interestRunService.calculatePreview(request, officerId);
        return ResponseEntity.created(URI.create("/api/v1/payroll/trust/interest/runs/" + preview.runId())).body(preview);
    }

    @PostMapping("/{runId}/post")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfAnnualInterestRunResponse post(@PathVariable Long runId, @Valid @RequestBody CpfInterestPostRequest request,
                                              Authentication authentication) {
        Long officerId = SecurityUtils.currentEmployeeId(authentication);
        return interestRunService.postRun(runId, request.acknowledgeDataReview(), officerId);
    }

    @PostMapping("/{runId}/reverse")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfAnnualInterestRunResponse reverse(@PathVariable Long runId, @Valid @RequestBody CpfInterestReverseRequest request,
                                                 Authentication authentication) {
        Long officerId = SecurityUtils.currentEmployeeId(authentication);
        return interestRunService.reverseRun(runId, request.reason(), officerId);
    }
}
