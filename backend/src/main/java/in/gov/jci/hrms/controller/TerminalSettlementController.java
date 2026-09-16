package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.TerminalSettlementBeneficiaryListRequest;
import in.gov.jci.hrms.dto.TerminalSettlementBeneficiaryResponse;
import in.gov.jci.hrms.dto.TerminalSettlementCalculation;
import in.gov.jci.hrms.dto.TerminalSettlementGenerateRequest;
import in.gov.jci.hrms.dto.TerminalSettlementResponse;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementBeneficiary;
import in.gov.jci.hrms.service.TerminalSettlementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Greenfield Terminal Settlement (Gratuity, DoPT Rule 39 EL/HPL encashment, CPF Trust ledger) + beneficiary disbursement. */
@RestController
@RequestMapping("/api/v1/settlements")
public class TerminalSettlementController {

    private final TerminalSettlementService terminalSettlementService;

    public TerminalSettlementController(TerminalSettlementService terminalSettlementService) {
        this.terminalSettlementService = terminalSettlementService;
    }

    @GetMapping("/preview/{employeeId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public TerminalSettlementResponse preview(@PathVariable Long employeeId,
                                               @RequestParam SeparationType separationType,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate separationDate,
                                               @RequestParam(required = false) Long clearanceRequestId,
                                               @RequestParam(required = false) BigDecimal cpfAccruedInterest) {
        TerminalSettlementCalculation calculation = terminalSettlementService.preview(
                employeeId, separationType, separationDate, clearanceRequestId, cpfAccruedInterest);
        return TerminalSettlementResponse.preview(calculation);
    }

    @PostMapping("/generate/{employeeId}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<TerminalSettlementResponse> generate(@PathVariable Long employeeId,
                                                                 @Valid @RequestBody TerminalSettlementGenerateRequest request) {
        TerminalSettlement settlement = terminalSettlementService.generate(
                employeeId, request.separationType(), request.separationDate(), request.clearanceRequestId(), request.cpfAccruedInterest());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(settlement));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public TerminalSettlementResponse getById(@PathVariable Long id) {
        return toResponse(terminalSettlementService.getById(id));
    }

    @PostMapping("/{id}/beneficiaries")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<TerminalSettlementBeneficiaryResponse> updateBeneficiaries(@PathVariable Long id,
                                                                            @Valid @RequestBody TerminalSettlementBeneficiaryListRequest request) {
        List<TerminalSettlementBeneficiary> saved = terminalSettlementService.replaceBeneficiaries(id, request.beneficiaries());
        return saved.stream().map(TerminalSettlementBeneficiaryResponse::from).toList();
    }

    // Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed per
    // confirmed direction ("SYSTEM_ADMIN must not independently approve terminal financial
    // settlement"). REQUIRES_BUSINESS_CONFIRMATION: the existing RBAC permission matrix has no distinct
    // checker permission for terminal settlement (unlike CPF/JCIECCS/Payroll, which have their own
    // *_ADMIN checker roles) - HR_ADMIN is retained here as the only currently-known authority, but
    // whether approval should require a role/permission distinct from whoever generates the settlement
    // (maker != checker, matching the pattern already enforced for CPF/JCIECCS) has not been decided and
    // is not invented here.
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public TerminalSettlementResponse approve(@PathVariable Long id) {
        return toResponse(terminalSettlementService.approve(id));
    }

    private TerminalSettlementResponse toResponse(TerminalSettlement settlement) {
        List<TerminalSettlementBeneficiaryResponse> beneficiaries = terminalSettlementService.beneficiaries(settlement.getId()).stream()
                .map(TerminalSettlementBeneficiaryResponse::from).toList();
        return TerminalSettlementResponse.from(settlement, beneficiaries);
    }
}
