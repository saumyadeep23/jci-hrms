package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfPassbookResponseDto;
import in.gov.jci.hrms.dto.CpfTrustLedgerEntryResponse;
import in.gov.jci.hrms.dto.CreditLedgerRequest;
import in.gov.jci.hrms.dto.CrystallizeInterimInterestRequest;
import in.gov.jci.hrms.dto.IncomingFundTransferRequest;
import in.gov.jci.hrms.dto.IncomingFundTransferResponse;
import in.gov.jci.hrms.dto.RejectRemarksRequest;
import in.gov.jci.hrms.entity.IncomingTransferStatus;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.service.CpfInterestComputationService;
import in.gov.jci.hrms.service.CpfTrustPassbookService;
import in.gov.jci.hrms.service.IncomingFundTransferService;
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

/** CPF Trust member passbook + incoming (transfer-in) fund workflow + annual interest declaration - same authorization convention as PfLedgerController (the existing CPF withdrawal/loan endpoint). */
@RestController
@RequestMapping("/api/v1/payroll/trust")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfTrustController {

    private final IncomingFundTransferService incomingFundTransferService;
    private final CpfInterestComputationService cpfInterestComputationService;
    private final CpfTrustPassbookService cpfTrustPassbookService;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;

    public CpfTrustController(IncomingFundTransferService incomingFundTransferService,
                               CpfInterestComputationService cpfInterestComputationService,
                               CpfTrustPassbookService cpfTrustPassbookService,
                               CpfTrustMemberLedgerEntryRepository ledgerRepository) {
        this.incomingFundTransferService = incomingFundTransferService;
        this.cpfInterestComputationService = cpfInterestComputationService;
        this.cpfTrustPassbookService = cpfTrustPassbookService;
        this.ledgerRepository = ledgerRepository;
    }

    /**
     * finYear is required (unlike the old all-years listing this endpoint used to return) because the
     * dynamic shadow-accrual projection and Para 60(2) rate resolution are both inherently scoped to one
     * FY - see CpfTrustPassbookService.
     */
    @GetMapping("/cpf/passbook/{employeeId}")
    public CpfPassbookResponseDto passbook(@PathVariable Long employeeId, @RequestParam String finYear) {
        return cpfTrustPassbookService.getPassbook(employeeId, finYear);
    }

    @PostMapping("/incoming-transfers")
    public ResponseEntity<IncomingFundTransferResponse> initiateTransfer(@Valid @RequestBody IncomingFundTransferRequest request,
                                                                           Authentication authentication) {
        Long loggedInUserId = in.gov.jci.hrms.security.SecurityUtils.currentEmployeeId(authentication);
        IncomingFundTransferResponse created = incomingFundTransferService.recordIncomingTransfer(request, loggedInUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payroll/trust/incoming-transfers/" + created.id()))
                .body(created);
    }

    @GetMapping("/incoming-transfers/pending")
    public List<IncomingFundTransferResponse> pendingTransfers() {
        return incomingFundTransferService.findPending();
    }

    @PutMapping("/incoming-transfers/{id}/credit-ledger")
    public IncomingFundTransferResponse creditLedger(@PathVariable Long id, @Valid @RequestBody CreditLedgerRequest request) {
        return incomingFundTransferService.verifyAndCreditTrustLedger(id, request);
    }

    @GetMapping("/incoming-transfers/all")
    public Page<IncomingFundTransferResponse> allTransfers(@RequestParam(required = false) IncomingTransferStatus status, Pageable pageable) {
        return incomingFundTransferService.findAll(status, pageable);
    }

    @PutMapping("/incoming-transfers/{id}/reject")
    public IncomingFundTransferResponse rejectTransfer(@PathVariable Long id, @Valid @RequestBody RejectRemarksRequest request) {
        return incomingFundTransferService.reject(id, request.remarks());
    }

    @PostMapping("/settlements/crystallize-interest")
    public CpfTrustLedgerEntryResponse crystallizeInterimInterest(@Valid @RequestBody CrystallizeInterimInterestRequest request,
                                                                    Authentication authentication) {
        Long officerId = in.gov.jci.hrms.security.SecurityUtils.currentEmployeeId(authentication);
        var posted = cpfInterestComputationService.crystallizeInterimInterest(request.employeeId(), request.settlementDate(),
                request.settlementType(), officerId);
        return CpfTrustLedgerEntryResponse.from(posted);
    }
}
