package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import in.gov.jci.hrms.service.CpfTrustMemberDirectoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CPF Trust Members' List - GET /api/v1/payroll/trust/members. Read-only directory, same role set as the rest of CpfTrustController. */
@RestController
@RequestMapping("/api/v1/payroll/trust/members")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfTrustMemberController {

    private final CpfTrustMemberDirectoryService memberDirectoryService;

    public CpfTrustMemberController(CpfTrustMemberDirectoryService memberDirectoryService) {
        this.memberDirectoryService = memberDirectoryService;
    }

    @GetMapping
    public Page<CpfTrustMemberResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean pendingSettlement,
            Pageable pageable) {
        return memberDirectoryService.list(status, search, pendingSettlement, pageable);
    }
}
