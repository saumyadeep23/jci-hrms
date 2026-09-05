package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.TadaClaimRequest;
import in.gov.jci.hrms.dto.TadaClaimResponse;
import in.gov.jci.hrms.dto.TadaClaimVerifyRequest;
import in.gov.jci.hrms.service.TadaClaimService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/reimbursements/tada")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class TadaClaimController {

    private final TadaClaimService tadaClaimService;

    public TadaClaimController(TadaClaimService tadaClaimService) {
        this.tadaClaimService = tadaClaimService;
    }

    @PostMapping
    public ResponseEntity<TadaClaimResponse> create(@Valid @RequestBody TadaClaimRequest request) {
        TadaClaimResponse created = tadaClaimService.create(request);
        return ResponseEntity.created(URI.create("/api/reimbursements/tada/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public TadaClaimResponse getById(@PathVariable Long id) {
        return tadaClaimService.getById(id);
    }

    @GetMapping
    public Page<TadaClaimResponse> list(Pageable pageable) {
        return tadaClaimService.list(pageable);
    }

    @PostMapping("/{id}/submit")
    public TadaClaimResponse submit(@PathVariable Long id) {
        return tadaClaimService.submit(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public TadaClaimResponse verifyByHr(@PathVariable Long id, @Valid @RequestBody TadaClaimVerifyRequest request) {
        return tadaClaimService.verifyByHr(id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'SUPER_ADMIN')")
    public TadaClaimResponse approveByFinance(@PathVariable Long id, @RequestParam String approvedBy) {
        return tadaClaimService.approveByFinance(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
    public TadaClaimResponse reject(@PathVariable Long id) {
        return tadaClaimService.reject(id);
    }
}
