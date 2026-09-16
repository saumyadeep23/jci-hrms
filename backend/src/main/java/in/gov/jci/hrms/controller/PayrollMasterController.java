package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PayrollHraRateRequest;
import in.gov.jci.hrms.dto.PayrollHraRateResponse;
import in.gov.jci.hrms.dto.ProcurementAllowanceRequest;
import in.gov.jci.hrms.dto.ProcurementAllowanceResponse;
import in.gov.jci.hrms.dto.PtaxSlabRequest;
import in.gov.jci.hrms.dto.PtaxSlabResponse;
import in.gov.jci.hrms.dto.SalaryHeadResponse;
import in.gov.jci.hrms.dto.SalaryHeadUpdateRequest;
import in.gov.jci.hrms.dto.StatutoryHeadResponse;
import in.gov.jci.hrms.dto.StatutoryHeadUpdateRequest;
import in.gov.jci.hrms.dto.StatutoryParameterResponse;
import in.gov.jci.hrms.dto.StatutoryParameterReviseRequest;
import in.gov.jci.hrms.dto.TransportAllowanceRequest;
import in.gov.jci.hrms.dto.TransportAllowanceResponse;
import in.gov.jci.hrms.service.PayrollHraRateService;
import in.gov.jci.hrms.service.PayrollMasterService;
import in.gov.jci.hrms.service.PayrollStatutoryParameterService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * JCI Payroll Engine master configuration screens: transport/procurement allowances, P-Tax slabs,
 * HRA rates, the salary/statutory head catalogs, and statutory parameters. NPS declarations moved to
 * their own NpsDeclarationController (/api/v1/payroll/declarations/nps) - see its javadoc.
 */
@RestController
@RequestMapping("/api/v1/payroll/masters")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN')")
public class PayrollMasterController {

    private final PayrollMasterService payrollMasterService;
    private final PayrollHraRateService payrollHraRateService;
    private final PayrollStatutoryParameterService payrollStatutoryParameterService;

    public PayrollMasterController(PayrollMasterService payrollMasterService, PayrollHraRateService payrollHraRateService,
                                    PayrollStatutoryParameterService payrollStatutoryParameterService) {
        this.payrollMasterService = payrollMasterService;
        this.payrollHraRateService = payrollHraRateService;
        this.payrollStatutoryParameterService = payrollStatutoryParameterService;
    }

    @GetMapping("/transport-allowances")
    public List<TransportAllowanceResponse> listTransportAllowances() {
        return payrollMasterService.listTransportAllowances();
    }

    @PostMapping("/transport-allowances")
    public ResponseEntity<TransportAllowanceResponse> createTransportAllowance(@Valid @RequestBody TransportAllowanceRequest request) {
        TransportAllowanceResponse created = payrollMasterService.createTransportAllowance(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/masters/transport-allowances/" + created.id())).body(created);
    }

    @PutMapping("/transport-allowances/{id}")
    public TransportAllowanceResponse updateTransportAllowance(@PathVariable Long id, @Valid @RequestBody TransportAllowanceRequest request) {
        return payrollMasterService.updateTransportAllowance(id, request);
    }

    @GetMapping("/procurement-allowances")
    public List<ProcurementAllowanceResponse> listProcurementAllowances() {
        return payrollMasterService.listProcurementAllowances();
    }

    @PostMapping("/procurement-allowances")
    public ResponseEntity<ProcurementAllowanceResponse> createProcurementAllowance(@Valid @RequestBody ProcurementAllowanceRequest request) {
        ProcurementAllowanceResponse created = payrollMasterService.createProcurementAllowance(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/masters/procurement-allowances/" + created.id())).body(created);
    }

    @PutMapping("/procurement-allowances/{id}")
    public ProcurementAllowanceResponse updateProcurementAllowance(@PathVariable Long id, @Valid @RequestBody ProcurementAllowanceRequest request) {
        return payrollMasterService.updateProcurementAllowance(id, request);
    }

    @GetMapping("/ptax-slabs")
    public List<PtaxSlabResponse> listPtaxSlabs() {
        return payrollMasterService.listPtaxSlabs();
    }

    @GetMapping("/ptax-slabs/by-state/{stateCode}")
    public List<PtaxSlabResponse> listPtaxSlabsByState(@PathVariable String stateCode) {
        return payrollMasterService.listPtaxSlabsByState(stateCode);
    }

    @PostMapping("/ptax-slabs")
    public ResponseEntity<PtaxSlabResponse> createPtaxSlab(@Valid @RequestBody PtaxSlabRequest request) {
        PtaxSlabResponse created = payrollMasterService.createPtaxSlab(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/masters/ptax-slabs/" + created.id())).body(created);
    }

    @PutMapping("/ptax-slabs/{id}")
    public PtaxSlabResponse updatePtaxSlab(@PathVariable Long id, @Valid @RequestBody PtaxSlabRequest request) {
        return payrollMasterService.updatePtaxSlab(id, request);
    }

    @GetMapping("/salary-heads")
    public List<SalaryHeadResponse> listSalaryHeads() {
        return payrollMasterService.listSalaryHeads();
    }

    // Non-financial RBAC migration (docs/security/RBAC_MIGRATION_REPORT.md): SUPER_ADMIN removed per
    // confirmed direction ("SYSTEM_ADMIN must not be an alternate functional approver for payroll/
    // statutory financial master changes merely because it is the technical administrator").
    // REQUIRES_BUSINESS_CONFIRMATION: the existing permission matrix's HR_ADMIN_BILL/HR_MAKER_BILL
    // roles carry only PAYROLL_VIEW/PREPARE/FINALIZE/REVERSE - no permission for editing salary-head/
    // statutory-head master data is seeded, so HR_ADMIN (the class-level's own read/write default role)
    // is retained here as the only currently-known authority rather than guessing HR_ADMIN_BILL applies.
    @PutMapping("/salary-heads/{headCount}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public SalaryHeadResponse updateSalaryHead(@PathVariable Integer headCount, @Valid @RequestBody SalaryHeadUpdateRequest request) {
        return payrollMasterService.updateSalaryHead(headCount, request);
    }

    @GetMapping("/statutory-heads")
    public List<StatutoryHeadResponse> listStatutoryHeads() {
        return payrollMasterService.listStatutoryHeads();
    }

    @PutMapping("/statutory-heads/{statHeadCount}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public StatutoryHeadResponse updateStatutoryHead(@PathVariable Integer statHeadCount,
                                                      @Valid @RequestBody StatutoryHeadUpdateRequest request) {
        return payrollMasterService.updateStatutoryHead(statHeadCount, request);
    }

    /** Omit {@code activeOn} to list every slab (including superseded/closed-out ones); pass it to list only slabs covering that date. */
    @GetMapping("/hra-rates")
    public List<PayrollHraRateResponse> listHraRates(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activeOn) {
        return activeOn != null ? payrollHraRateService.listActiveOn(activeOn) : payrollHraRateService.listAll();
    }

    @PostMapping("/hra-rates")
    public ResponseEntity<PayrollHraRateResponse> createHraRate(@Valid @RequestBody PayrollHraRateRequest request) {
        PayrollHraRateResponse created = payrollHraRateService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/masters/hra-rates/" + created.id())).body(created);
    }

    /** Also how a slab is closed out - send the same slab back with effectiveTo set. */
    @PutMapping("/hra-rates/{id}")
    public PayrollHraRateResponse updateHraRate(@PathVariable Long id, @Valid @RequestBody PayrollHraRateRequest request) {
        return payrollHraRateService.update(id, request);
    }

    @GetMapping("/statutory-parameters")
    public List<StatutoryParameterResponse> listStatutoryParameters() {
        return payrollStatutoryParameterService.listCurrent();
    }

    // REQUIRES_BUSINESS_CONFIRMATION (same reasoning as updateSalaryHead/updateStatutoryHead above) -
    // HR_ADMIN/FINANCE_ADMIN (the two roles already present) retained, SUPER_ADMIN removed.
    @PostMapping("/statutory-parameters/{paramKey}/revise")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public StatutoryParameterResponse reviseStatutoryParameter(@PathVariable String paramKey,
                                                                @Valid @RequestBody StatutoryParameterReviseRequest request) {
        return payrollStatutoryParameterService.revise(paramKey, request);
    }
}
