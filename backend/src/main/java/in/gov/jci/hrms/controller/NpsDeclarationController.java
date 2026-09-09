package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.NpsAdminSummaryResponse;
import in.gov.jci.hrms.dto.NpsDeclarationRequest;
import in.gov.jci.hrms.dto.NpsDeclarationResponse;
import in.gov.jci.hrms.dto.NpsPreviewResponse;
import in.gov.jci.hrms.service.NpsDeclarationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Employee NPS Declaration Desk & HR Compliance Dashboard - supersedes the old /api/v1/payroll/masters/nps-declarations endpoints. */
@RestController
@RequestMapping("/api/v1/payroll/declarations/nps")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN')")
public class NpsDeclarationController {

    private final NpsDeclarationService npsDeclarationService;

    public NpsDeclarationController(NpsDeclarationService npsDeclarationService) {
        this.npsDeclarationService = npsDeclarationService;
    }

    @GetMapping("/employee/{employeeId}/preview")
    public NpsPreviewResponse preview(@PathVariable Long employeeId) {
        return npsDeclarationService.preview(employeeId);
    }

    @PostMapping
    public ResponseEntity<NpsDeclarationResponse> recordDeclaration(@Valid @RequestBody NpsDeclarationRequest request) {
        NpsDeclarationResponse created = npsDeclarationService.recordDeclaration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/admin/summary")
    public NpsAdminSummaryResponse adminSummary(@RequestParam String fy) {
        return npsDeclarationService.adminSummary(fy);
    }
}
