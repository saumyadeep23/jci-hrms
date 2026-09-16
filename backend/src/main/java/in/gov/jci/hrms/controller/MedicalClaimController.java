package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.MedicalClaimRequest;
import in.gov.jci.hrms.dto.MedicalClaimResponse;
import in.gov.jci.hrms.dto.MedicalClaimVerifyRequest;
import in.gov.jci.hrms.service.MedicalClaimService;
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
@RequestMapping("/api/reimbursements/medical")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'FINANCE_ADMIN')")
public class MedicalClaimController {

    private final MedicalClaimService medicalClaimService;

    public MedicalClaimController(MedicalClaimService medicalClaimService) {
        this.medicalClaimService = medicalClaimService;
    }

    @PostMapping
    public ResponseEntity<MedicalClaimResponse> create(@Valid @RequestBody MedicalClaimRequest request) {
        MedicalClaimResponse created = medicalClaimService.create(request);
        return ResponseEntity.created(URI.create("/api/reimbursements/medical/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public MedicalClaimResponse getById(@PathVariable Long id) {
        return medicalClaimService.getById(id);
    }

    @GetMapping
    public Page<MedicalClaimResponse> list(Pageable pageable) {
        return medicalClaimService.list(pageable);
    }

    @PostMapping("/{id}/submit")
    public MedicalClaimResponse submit(@PathVariable Long id) {
        return medicalClaimService.submit(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public MedicalClaimResponse verifyByHr(@PathVariable Long id, @Valid @RequestBody MedicalClaimVerifyRequest request) {
        return medicalClaimService.verifyByHr(id, request);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public MedicalClaimResponse approveByFinance(@PathVariable Long id, @RequestParam String approvedBy) {
        return medicalClaimService.approveByFinance(id, approvedBy);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public MedicalClaimResponse reject(@PathVariable Long id) {
        return medicalClaimService.reject(id);
    }
}
