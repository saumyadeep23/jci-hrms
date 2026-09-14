package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfEpsEligibilityResponse;
import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import in.gov.jci.hrms.dto.CpfTrustMemberSummaryResponse;
import in.gov.jci.hrms.dto.UpdateUanRequest;
import in.gov.jci.hrms.service.CpfEpsEligibilityService;
import in.gov.jci.hrms.service.CpfTrustMemberDirectoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** CPF Trust Members' List - GET /api/v1/payroll/trust/members(/summary), plus the member-scoped UAN-update and EPS-eligibility actions. Same role set as the rest of CpfTrustController. */
@RestController
@RequestMapping("/api/v1/payroll/trust/members")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfTrustMemberController {

    private final CpfTrustMemberDirectoryService memberDirectoryService;
    private final CpfEpsEligibilityService epsEligibilityService;

    public CpfTrustMemberController(CpfTrustMemberDirectoryService memberDirectoryService,
                                     CpfEpsEligibilityService epsEligibilityService) {
        this.memberDirectoryService = memberDirectoryService;
        this.epsEligibilityService = epsEligibilityService;
    }

    @GetMapping
    public Page<CpfTrustMemberResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(defaultValue = "false") boolean uanMissing,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate separationFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate separationTo,
            Pageable pageable) {
        return memberDirectoryService.list(status, search, settlementStatus, uanMissing, separationFrom, separationTo, pageable);
    }

    @GetMapping("/summary")
    public CpfTrustMemberSummaryResponse summary() {
        return memberDirectoryService.summary();
    }

    @GetMapping("/{employeeId}/eps-eligibility")
    public CpfEpsEligibilityResponse epsEligibility(@PathVariable Long employeeId) {
        return epsEligibilityService.checkEligibility(employeeId);
    }

    @PutMapping("/{employeeId}/uan")
    public CpfTrustMemberResponse updateUan(@PathVariable Long employeeId, @Valid @RequestBody UpdateUanRequest request) {
        return memberDirectoryService.updateUan(employeeId, request.uanNo());
    }
}
