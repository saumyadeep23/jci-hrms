package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EditPayrollLineDto;
import in.gov.jci.hrms.dto.PayrollBatchCreateRequest;
import in.gov.jci.hrms.dto.PayrollBatchDetailsResponse;
import in.gov.jci.hrms.dto.PayrollBatchResponse;
import in.gov.jci.hrms.dto.PayrollEditResponse;
import in.gov.jci.hrms.dto.PayrollMonthlyRecordResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.service.CpfLedgerSyncService;
import in.gov.jci.hrms.service.PayrollBatchComputationService;
import in.gov.jci.hrms.service.PayrollBatchEditService;
import in.gov.jci.hrms.service.PayrollBatchService;
import in.gov.jci.hrms.service.PayrollReportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * Lifecycle endpoints for the unified Payroll Computation Engine's batches (payroll_batches) - see
 * {@link PayrollBatchComputationService} for the actual Earnings/Deductions/HRA-suppression/Head
 * 20/Section 192 TDS computation. create()/list() (V78) are new - previously a payroll_batches row had
 * to already exist, by some other means, before /calculate could be called.
 */
@RestController
@RequestMapping("/api/v1/payroll/batches")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'BILL_SUPERVISOR', 'FINANCE_ADMIN')")
public class PayrollBatchController {

    private final PayrollBatchComputationService payrollBatchComputationService;
    private final CpfLedgerSyncService cpfLedgerSyncService;
    private final PayrollBatchService payrollBatchService;
    private final PayrollBatchEditService payrollBatchEditService;
    private final PayrollReportService payrollReportService;

    public PayrollBatchController(PayrollBatchComputationService payrollBatchComputationService, CpfLedgerSyncService cpfLedgerSyncService,
                                   PayrollBatchService payrollBatchService, PayrollBatchEditService payrollBatchEditService,
                                   PayrollReportService payrollReportService) {
        this.payrollBatchComputationService = payrollBatchComputationService;
        this.cpfLedgerSyncService = cpfLedgerSyncService;
        this.payrollBatchService = payrollBatchService;
        this.payrollBatchEditService = payrollBatchEditService;
        this.payrollReportService = payrollReportService;
    }

    @PostMapping
    public ResponseEntity<PayrollBatchResponse> create(@Valid @RequestBody PayrollBatchCreateRequest request) {
        PayrollBatchResponse created = payrollBatchService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/payroll/batches/" + created.id()))
                .body(created);
    }

    @GetMapping
    public Page<PayrollBatchResponse> list(Pageable pageable) {
        return payrollBatchService.list(pageable);
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

    /** Batch summary plus every employee's full zero-filled head matrix - unpaginated by default (Bill Section reviews the whole batch, not a page of it); pass a smaller `size` to page it. */
    @GetMapping("/{batchId}/details")
    public PayrollBatchDetailsResponse details(@PathVariable Long batchId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "2000") int size) {
        PayrollBatchResponse batch = payrollBatchService.getById(batchId);
        Page<PayrollMonthlyRecordResponse> records = payrollBatchComputationService.listRecords(batchId, PageRequest.of(page, size));
        return new PayrollBatchDetailsResponse(batch, records.getContent());
    }

    /** New: HR_FINALIZED/FINANCE_APPROVED -> DISBURSED, and syncs the CPF Trust ledger for every employee in the batch - see CpfLedgerSyncService's own javadoc for why this transition didn't already exist. */
    @PostMapping("/{batchId}/disburse")
    public PayrollBatchResponse disburse(@PathVariable Long batchId, @RequestParam(required = false) Long disbursedByEmployeeId) {
        return PayrollBatchResponse.from(cpfLedgerSyncService.disburse(batchId, disbursedByEmployeeId));
    }

    @GetMapping("/{batchId}/records/{tranId}/edit-preview")
    public PayrollEditResponse editPreview(@PathVariable Long batchId, @PathVariable Long tranId,
                                            @RequestParam Integer headCount, @RequestParam java.math.BigDecimal newAmount) {
        return payrollBatchEditService.previewSalaryHead(batchId, tranId, new EditPayrollLineDto(headCount, newAmount, ""));
    }

    @PostMapping("/{batchId}/records/{tranId}/edit")
    public PayrollEditResponse edit(@PathVariable Long batchId, @PathVariable Long tranId,
                                     @Valid @RequestBody EditPayrollLineDto dto, @RequestParam(required = false) Long officerId) {
        return payrollBatchEditService.editSalaryHead(batchId, tranId, dto, officerId);
    }

    @GetMapping("/{batchId}/reports/{reportType}")
    public ResponseEntity<?> report(@PathVariable Long batchId, @PathVariable PayrollReportService.ReportType reportType,
                                     @RequestParam(defaultValue = "JSON") PayrollReportService.ExportFormat format) {
        TabularReportResponse data = payrollReportService.generate(batchId, reportType);
        if (format == PayrollReportService.ExportFormat.CSV) {
            byte[] csv = payrollReportService.renderCsv(data);
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(reportType.name().toLowerCase() + "-batch-" + batchId + ".csv")
                    .build();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(csv);
        }
        return ResponseEntity.ok(data);
    }
}
