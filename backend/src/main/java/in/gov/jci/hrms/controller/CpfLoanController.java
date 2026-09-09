package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanEligibilityResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementQuoteResponse;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementResponse;
import in.gov.jci.hrms.dto.RejectRemarksRequest;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.CpfLoanApplicationService;
import in.gov.jci.hrms.service.CpfLoanSettlementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** CPF Trust loan/withdrawal origination lifecycle - same authorization convention as CpfTrustController/PfLedgerController. */
@RestController
@RequestMapping("/api/v1/payroll/trust/loans")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfLoanController {

    private final CpfLoanApplicationService cpfLoanApplicationService;
    private final CpfLoanSettlementService cpfLoanSettlementService;

    public CpfLoanController(CpfLoanApplicationService cpfLoanApplicationService, CpfLoanSettlementService cpfLoanSettlementService) {
        this.cpfLoanApplicationService = cpfLoanApplicationService;
        this.cpfLoanSettlementService = cpfLoanSettlementService;
    }

    @GetMapping("/eligibility/{employeeId}")
    public CpfLoanEligibilityResponse eligibility(@PathVariable Long employeeId, @RequestParam CpfLoanType loanType,
                                                    @RequestParam(required = false) String purpose) {
        return cpfLoanApplicationService.checkEligibility(employeeId, loanType, purpose);
    }

    @PostMapping("/apply")
    public ResponseEntity<CpfLoanApplicationResponse> apply(@Valid @RequestBody CpfLoanApplicationRequest request, Authentication authentication) {
        Long applicantUserId = SecurityUtils.currentEmployeeId(authentication);
        CpfLoanApplicationResponse created = cpfLoanApplicationService.applyLoan(request, applicantUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payroll/trust/loans/" + created.id()))
                .body(created);
    }

    @GetMapping("/pending")
    public List<CpfLoanApplicationResponse> pending() {
        return cpfLoanApplicationService.findPending();
    }

    @GetMapping("/employee/{employeeId}")
    public List<CpfLoanApplicationResponse> byEmployee(@PathVariable Long employeeId) {
        return cpfLoanApplicationService.findByEmployee(employeeId);
    }

    /** Org-wide, any-status listing (not in the original endpoint spec) - see CpfLoanApplicationService.findAll()'s own javadoc for why the queue table/metric cards need this beyond /pending and /employee/{id}. */
    @GetMapping("/all")
    public Page<CpfLoanApplicationResponse> all(@RequestParam(required = false) CpfLoanApplicationStatus status, Pageable pageable) {
        return cpfLoanApplicationService.findAll(status, pageable);
    }

    @PutMapping("/{id}/sanction")
    public CpfLoanApplicationResponse sanction(@PathVariable Long id, @Valid @RequestBody CpfLoanSanctionRequest request,
                                                 Authentication authentication) {
        Long approvingOfficerId = SecurityUtils.currentEmployeeId(authentication);
        return cpfLoanApplicationService.sanctionLoan(id, request, approvingOfficerId);
    }

    @PutMapping("/{id}/disburse")
    public CpfLoanApplicationResponse disburse(@PathVariable Long id, Authentication authentication) {
        Long disburseOfficerId = SecurityUtils.currentEmployeeId(authentication);
        return cpfLoanApplicationService.disburseLoan(id, disburseOfficerId);
    }

    @PutMapping("/{id}/reject")
    public CpfLoanApplicationResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRemarksRequest request,
                                               Authentication authentication) {
        Long rejectingOfficerId = SecurityUtils.currentEmployeeId(authentication);
        return cpfLoanApplicationService.rejectLoan(id, request.remarks(), rejectingOfficerId);
    }

    @GetMapping("/{id}/settlement-quote")
    public CpfLoanSettlementQuoteResponse settlementQuote(@PathVariable Long id) {
        return cpfLoanSettlementService.calculateEarlySettlementQuote(id);
    }

    @PostMapping("/{id}/settle-cash")
    public CpfLoanSettlementResponse settleCash(@PathVariable Long id, @Valid @RequestBody CpfLoanSettlementRequest request,
                                                  Authentication authentication) {
        Long receivedByOfficerId = SecurityUtils.currentEmployeeId(authentication);
        return cpfLoanSettlementService.processCashSettlement(id, request, receivedByOfficerId);
    }
}
