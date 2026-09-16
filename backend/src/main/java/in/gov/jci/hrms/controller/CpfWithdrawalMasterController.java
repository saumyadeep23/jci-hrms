package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfHeadMasterResponse;
import in.gov.jci.hrms.dto.CpfPayrollDeductionCapRequest;
import in.gov.jci.hrms.dto.CpfPayrollDeductionCapResponse;
import in.gov.jci.hrms.dto.CpfWithdrawalPurposeResponse;
import in.gov.jci.hrms.dto.CpfWithdrawalTypeResponse;
import in.gov.jci.hrms.service.CpfPayrollDeductionCapService;
import in.gov.jci.hrms.service.CpfWithdrawalMasterService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Withdrawal Type/Purpose/Head master lookups (Parts 5-7) plus Payroll Deduction Cap (Part 18) - same authorization convention as CpfTrustController. */
@RestController
@RequestMapping("/api/v1/payroll/trust/withdrawal-masters")
@PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
public class CpfWithdrawalMasterController {

    private final CpfWithdrawalMasterService masterService;
    private final CpfPayrollDeductionCapService deductionCapService;

    public CpfWithdrawalMasterController(CpfWithdrawalMasterService masterService, CpfPayrollDeductionCapService deductionCapService) {
        this.masterService = masterService;
        this.deductionCapService = deductionCapService;
    }

    @GetMapping("/types")
    public List<CpfWithdrawalTypeResponse> types() {
        return masterService.listTypes();
    }

    @GetMapping("/purposes")
    public List<CpfWithdrawalPurposeResponse> purposes() {
        return masterService.listPurposes();
    }

    @GetMapping("/heads")
    public List<CpfHeadMasterResponse> heads() {
        return masterService.listHeads();
    }

    @GetMapping("/payroll-deduction-cap")
    public CpfPayrollDeductionCapResponse currentDeductionCap() {
        return deductionCapService.getCurrent();
    }

    @GetMapping("/payroll-deduction-cap/history")
    public List<CpfPayrollDeductionCapResponse> deductionCapHistory() {
        return deductionCapService.history();
    }

    @PostMapping("/payroll-deduction-cap")
    @PreAuthorize("hasRole('CPF_ADMIN')")
    public CpfPayrollDeductionCapResponse reviseDeductionCap(@Valid @RequestBody CpfPayrollDeductionCapRequest request) {
        return deductionCapService.revise(request);
    }
}
