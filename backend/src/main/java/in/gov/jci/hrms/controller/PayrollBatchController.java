package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PayrollBatchResponse;
import in.gov.jci.hrms.dto.PayrollMonthlyRecordResponse;
import in.gov.jci.hrms.service.CpfLedgerSyncService;
import in.gov.jci.hrms.service.PayrollBatchComputationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lifecycle endpoints for the unified Payroll Computation Engine's batches (payroll_batches) - see
 * {@link PayrollBatchComputationService} for the actual Earnings/Deductions/HRA-suppression/Head
 * 20/Section 192 TDS computation. Batch creation itself is out of scope here (no create/list endpoint
 * is specified) - a payroll_batches row is expected to already exist in DRAFT status before calculate()
 * is called.
 */
@RestController
@RequestMapping("/api/v1/payroll/batches")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN')")
public class PayrollBatchController {

    private final PayrollBatchComputationService payrollBatchComputationService;
    private final CpfLedgerSyncService cpfLedgerSyncService;

    public PayrollBatchController(PayrollBatchComputationService payrollBatchComputationService, CpfLedgerSyncService cpfLedgerSyncService) {
        this.payrollBatchComputationService = payrollBatchComputationService;
        this.cpfLedgerSyncService = cpfLedgerSyncService;
    }

    @PostMapping("/{batchId}/calculate")
    public PayrollBatchResponse calculate(@PathVariable Long batchId) {
        return PayrollBatchResponse.from(payrollBatchComputationService.processBatch(batchId));
    }

    @PostMapping("/{batchId}/finalize")
    public PayrollBatchResponse finalizeBatch(@PathVariable Long batchId,
                                               @RequestParam(required = false) Long finalizedByEmployeeId) {
        return PayrollBatchResponse.from(payrollBatchComputationService.finalizeBatch(batchId, finalizedByEmployeeId));
    }

    @GetMapping("/{batchId}/records")
    public Page<PayrollMonthlyRecordResponse> records(@PathVariable Long batchId, Pageable pageable) {
        return payrollBatchComputationService.listRecords(batchId, pageable);
    }

    /** New: HR_FINALIZED/FINANCE_APPROVED -> DISBURSED, and syncs the CPF Trust ledger for every employee in the batch - see CpfLedgerSyncService's own javadoc for why this transition didn't already exist. */
    @PostMapping("/{batchId}/disburse")
    public PayrollBatchResponse disburse(@PathVariable Long batchId, @RequestParam(required = false) Long disbursedByEmployeeId) {
        return PayrollBatchResponse.from(cpfLedgerSyncService.disburse(batchId, disbursedByEmployeeId));
    }
}
