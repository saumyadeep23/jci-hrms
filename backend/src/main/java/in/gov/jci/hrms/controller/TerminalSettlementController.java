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

    // Final RBAC business-authority closure (docs/security/RBAC_MIGRATION_REPORT.md), confirmed
    // direction: approve() IS the final financial authorization currently implemented in this
    // lifecycle (DRAFT -> [AUDITED, never reached] -> APPROVED -> [DISBURSED, no code path reaches
    // it yet] - see TerminalSettlementService.approve()'s own trace) - it validates beneficiary bank
    // allocation and irreversibly debits the encashed leave ledger, i.e. it is the point past which
    // the settlement is authorized for payment, not merely an HR data-verification step. It therefore
    // requires DISBURSEMENT_AUTHORIZE (FIN_ADMIN_DISB's permission, reused as-is - no new permission
    // needed for this endpoint). HR_ADMIN keeps preview/generate/getById/updateBeneficiaries (the HR
    // preparation stage) unchanged, but does NOT get this endpoint - SYSTEM_ADMIN and HR_ADMIN are
    // both denied here.
    //
    // FOLLOW_UP_REQUIRED - TERMINAL_SETTLEMENT_MAKER_CHECKER: TerminalSettlement has no preparer/
    // generatedBy actor column (only `employee`, the settlement's subject, and createdAt/updatedAt
    // timestamps) - there is no existing data to compare "who generated this settlement" against
    // "who is approving it" the way CpfLoanApplication.applicantEmployeeId supports SEC-003's
    // requireDifferentFromApplicant(). Enforcing preparer != approver here would require a schema
    // change (a new actor column) and is not invented in this closure task.
    @PostMapping("/{id}/approve")
    @PreAuthorize("@rbac.hasPermission(authentication, 'DISBURSEMENT_AUTHORIZE')")
    public TerminalSettlementResponse approve(@PathVariable Long id) {
        return toResponse(terminalSettlementService.approve(id));
    }

    private TerminalSettlementResponse toResponse(TerminalSettlement settlement) {
        List<TerminalSettlementBeneficiaryResponse> beneficiaries = terminalSettlementService.beneficiaries(settlement.getId()).stream()
                .map(TerminalSettlementBeneficiaryResponse::from).toList();
        return TerminalSettlementResponse.from(settlement, beneficiaries);
    }
}
