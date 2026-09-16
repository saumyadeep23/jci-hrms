package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PayrollRunRequest;
import in.gov.jci.hrms.dto.PayrollRunResponse;
import in.gov.jci.hrms.dto.PayslipResponse;
import in.gov.jci.hrms.service.PayrollRunService;
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
import java.util.List;

@RestController
@RequestMapping("/api/payroll/runs")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'SUPER_ADMIN')")
public class PayrollRunController {

    private final PayrollRunService payrollRunService;

    public PayrollRunController(PayrollRunService payrollRunService) {
        this.payrollRunService = payrollRunService;
    }

    @PostMapping
    public ResponseEntity<PayrollRunResponse> create(@Valid @RequestBody PayrollRunRequest request) {
        PayrollRunResponse created = payrollRunService.create(request);
        return ResponseEntity.created(URI.create("/api/payroll/runs/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public PayrollRunResponse getById(@PathVariable Long id) {
        return payrollRunService.getById(id);
    }

    @GetMapping
    public Page<PayrollRunResponse> list(Pageable pageable) {
        return payrollRunService.list(pageable);
    }

    @PostMapping("/{id}/compute")
    public PayrollRunResponse compute(@PathVariable Long id) {
        return payrollRunService.compute(id);
    }

    // SEC-010 (docs/security/RBAC_MIGRATION_REPORT.md): payroll finalization is a checker-only financial
    // approval action - narrowed off the class-level SUPER_ADMIN grant so the legacy god-role cannot
    // finalize a payroll run; only FINANCE_ADMIN (the class-level's other, functional role) can.
    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public PayrollRunResponse finalizeRun(@PathVariable Long id, @RequestParam String finalizedBy) {
        return payrollRunService.finalizeRun(id, finalizedBy);
    }

    @GetMapping("/{id}/payslips")
    public List<PayslipResponse> listPayslips(@PathVariable Long id) {
        return payrollRunService.listPayslips(id);
    }
}
