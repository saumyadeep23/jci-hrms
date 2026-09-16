package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PayslipSummaryResponse;
import in.gov.jci.hrms.service.PayrollReportingService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final PayrollReportingService payrollReportingService;

    public ReportingController(PayrollReportingService payrollReportingService) {
        this.payrollReportingService = payrollReportingService;
    }

    @GetMapping("/payroll/{payrollRunId}/bank-file")
    @PreAuthorize("hasRole('FINANCE_ADMIN')")
    public ResponseEntity<String> bankDisbursementFile(@PathVariable Long payrollRunId) {
        String csv = payrollReportingService.generateBankDisbursementFile(payrollRunId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("bank-disbursement-run-" + payrollRunId + ".csv")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }

    @GetMapping("/payroll/{payrollRunId}/payslip/{employeeId}")
    @PreAuthorize("hasRole('FINANCE_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public PayslipSummaryResponse payslipSummary(@PathVariable Long payrollRunId, @PathVariable Long employeeId) {
        return payrollReportingService.getPayslipSummary(payrollRunId, employeeId);
    }
}
