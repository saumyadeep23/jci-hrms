package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.EssSalarySlipDetailResponse;
import in.gov.jci.hrms.dto.EssSalarySlipSummaryResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.EssPayrollService;
import in.gov.jci.hrms.service.pdf.SalarySlipPdfGenerator;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ESS "My Salary Slips" - see EssPayrollService for the DISBURSED-only visibility rule. */
@RestController
public class EssPayrollController {

    private final EssPayrollService essPayrollService;
    private final SalarySlipPdfGenerator salarySlipPdfGenerator;

    public EssPayrollController(EssPayrollService essPayrollService, SalarySlipPdfGenerator salarySlipPdfGenerator) {
        this.essPayrollService = essPayrollService;
        this.salarySlipPdfGenerator = salarySlipPdfGenerator;
    }

    @GetMapping("/api/v1/ess/salary-slips")
    @PreAuthorize("isAuthenticated()")
    public List<EssSalarySlipSummaryResponse> list(Authentication authentication) {
        return essPayrollService.list(requireCurrentEmployeeId(authentication));
    }

    @GetMapping("/api/v1/ess/salary-slips/{tranId}")
    @PreAuthorize("isAuthenticated()")
    public EssSalarySlipDetailResponse detail(@PathVariable Long tranId, Authentication authentication) {
        return essPayrollService.getDetail(requireCurrentEmployeeId(authentication), tranId);
    }

    @GetMapping("/api/v1/ess/salary-slips/{tranId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable Long tranId, Authentication authentication) {
        EssSalarySlipDetailResponse slip = essPayrollService.getDetail(requireCurrentEmployeeId(authentication), tranId);
        byte[] pdf = salarySlipPdfGenerator.generate(slip);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("salary-slip-" + slip.year() + "-" + String.format("%02d", slip.month()) + ".pdf")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }

    private Long requireCurrentEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve whose salary slips these are");
        }
        return employeeId;
    }
}
