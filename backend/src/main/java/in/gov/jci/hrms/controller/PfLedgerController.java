package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfBalanceLedgerRequest;
import in.gov.jci.hrms.dto.CpfBalanceLedgerResponse;
import in.gov.jci.hrms.dto.NonRefundableWithdrawalRequest;
import in.gov.jci.hrms.dto.PfDiversionResponse;
import in.gov.jci.hrms.dto.RefundableLoanDiversionRequest;
import in.gov.jci.hrms.service.PfLedgerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/cpf-ledger")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN', 'SUPER_ADMIN')")
public class PfLedgerController {

    private final PfLedgerService pfLedgerService;

    public PfLedgerController(PfLedgerService pfLedgerService) {
        this.pfLedgerService = pfLedgerService;
    }

    @PostMapping
    public ResponseEntity<CpfBalanceLedgerResponse> createLedger(@Valid @RequestBody CpfBalanceLedgerRequest request) {
        CpfBalanceLedgerResponse created = pfLedgerService.createLedger(request);
        return ResponseEntity.created(URI.create("/api/cpf-ledger/" + created.employeeId())).body(created);
    }

    @GetMapping("/{employeeId}")
    public CpfBalanceLedgerResponse getByEmployeeId(@PathVariable Long employeeId) {
        return pfLedgerService.getLedgerByEmployeeId(employeeId);
    }

    @PostMapping("/diversions/refundable-loan")
    public ResponseEntity<PfDiversionResponse> createRefundableLoanDiversion(
            @Valid @RequestBody RefundableLoanDiversionRequest request) {
        PfDiversionResponse created = pfLedgerService.createRefundableLoanDiversion(request);
        return ResponseEntity.created(URI.create("/api/cpf-ledger/diversions/" + created.id())).body(created);
    }

    @PostMapping("/diversions/non-refundable-withdrawal")
    public ResponseEntity<PfDiversionResponse> createNonRefundableWithdrawal(
            @Valid @RequestBody NonRefundableWithdrawalRequest request) {
        PfDiversionResponse created = pfLedgerService.createNonRefundableWithdrawal(request);
        return ResponseEntity.created(URI.create("/api/cpf-ledger/diversions/" + created.id())).body(created);
    }

    // Non-financial RBAC migration pass (docs/security/RBAC_MIGRATION_REPORT.md): missed by the earlier
    // SEC-003/004 closure - settle is a checker-shaped financial action, SUPER_ADMIN removed here too.
    @PostMapping("/diversions/{id}/settle")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'COOP_ADMIN')")
    public PfDiversionResponse settle(@PathVariable Long id) {
        return pfLedgerService.settle(id);
    }
}
