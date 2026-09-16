package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ClarificationRequest;
import in.gov.jci.hrms.dto.EmployeeMovementRecordResponse;
import in.gov.jci.hrms.dto.JoiningDecisionRequest;
import in.gov.jci.hrms.dto.JoiningReportRequest;
import in.gov.jci.hrms.dto.MovementOrderCreateRequest;
import in.gov.jci.hrms.dto.MovementReleaseRequest;
import in.gov.jci.hrms.dto.PayrollMovementInputResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JoiningReportService;
import in.gov.jci.hrms.service.LastPayCertificateService;
import in.gov.jci.hrms.service.MovementOrderService;
import in.gov.jci.hrms.service.PayrollMovementIntegrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Transfer/Promotion/Release/Joining-Report lifecycle - see JoiningReportService/MovementOrderService/PayrollMovementIntegrationService/LastPayCertificateService for the business logic behind each endpoint. */
@RestController
@RequestMapping("/api/v1/pims/movements")
public class MovementLifecycleController {

    private final MovementOrderService movementOrderService;
    private final JoiningReportService joiningReportService;
    private final PayrollMovementIntegrationService payrollMovementIntegrationService;
    private final LastPayCertificateService lastPayCertificateService;

    public MovementLifecycleController(MovementOrderService movementOrderService, JoiningReportService joiningReportService,
                                        PayrollMovementIntegrationService payrollMovementIntegrationService,
                                        LastPayCertificateService lastPayCertificateService) {
        this.movementOrderService = movementOrderService;
        this.joiningReportService = joiningReportService;
        this.payrollMovementIntegrationService = payrollMovementIntegrationService;
        this.lastPayCertificateService = lastPayCertificateService;
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename).build().toString())
                .body(content);
    }

    private Long requireEmployeeId(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve which movement records are yours");
        }
        return employeeId;
    }

    // ---- Tab 1: Movement Orders ----

    @PostMapping("/orders")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<EmployeeMovementRecordResponse> createOrder(@Valid @RequestBody MovementOrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movementOrderService.create(request));
    }

    @GetMapping("/records")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Page<EmployeeMovementRecordResponse> list(Pageable pageable) {
        return movementOrderService.list(pageable);
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeMovementRecordResponse getById(@PathVariable Long id) {
        return movementOrderService.getById(id);
    }

    @GetMapping("/records/mine")
    @PreAuthorize("isAuthenticated()")
    public List<EmployeeMovementRecordResponse> mine(Authentication authentication) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException("Your token has no employee_id claim - cannot resolve your movement records");
        }
        return joiningReportService.mine(employeeId);
    }

    /** Transfer or Promotion Order PDF (dispatched by order type) - Tab 1's "Download Order PDF" button. */
    @GetMapping("/orders/{orderId}/pdf")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<byte[]> orderPdf(@PathVariable Long orderId) {
        return pdfResponse(movementOrderService.generateOrderPdf(orderId), "Movement_Order_" + orderId + ".pdf");
    }

    /** ESS: the same Order PDF, but scoped to a movement record the caller is actually named in (never another employee's, even within the same multi-employee order). */
    @GetMapping("/records/{id}/my-documents/order-pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myOrderPdf(@PathVariable Long id, Authentication authentication) {
        Long employeeId = requireEmployeeId(authentication);
        return pdfResponse(movementOrderService.generateOrderPdfForEmployee(id, employeeId), "Movement_Order_" + id + ".pdf");
    }

    // ---- Tab 2: Pending Releases ----

    @GetMapping("/records/pending-release")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<EmployeeMovementRecordResponse> pendingReleases() {
        return joiningReportService.pendingReleases();
    }

    @PatchMapping("/records/{id}/release")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeMovementRecordResponse release(@PathVariable Long id, @Valid @RequestBody MovementReleaseRequest request) {
        return joiningReportService.release(id, request);
    }

    /** Release Order PDF - Tab 2's "Download Release Order PDF" button. */
    @GetMapping("/records/{id}/release/pdf")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public ResponseEntity<byte[]> releasePdf(@PathVariable Long id) {
        return pdfResponse(joiningReportService.generateReleasePdf(id), "Release_Order_" + id + ".pdf");
    }

    /** ESS: the same Release Order PDF, scoped to the caller's own record. */
    @GetMapping("/records/{id}/my-documents/release-pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myReleasePdf(@PathVariable Long id, Authentication authentication) {
        Long employeeId = requireEmployeeId(authentication);
        return pdfResponse(joiningReportService.generateReleasePdfForEmployee(id, employeeId), "Release_Order_" + id + ".pdf");
    }

    // ---- Tab 3: Joining Verifications ----

    @GetMapping("/records/pending-joining")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<EmployeeMovementRecordResponse> pendingJoiningVerifications() {
        return joiningReportService.pendingJoiningVerifications();
    }

    @PostMapping("/records/{id}/joining-report")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN')")
    public EmployeeMovementRecordResponse submitJoiningReport(@PathVariable Long id, @Valid @RequestBody JoiningReportRequest request,
                                                                Authentication authentication, HttpServletRequest httpRequest) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        return joiningReportService.submitJoiningReport(id, request, employeeId, httpRequest.getRemoteAddr());
    }

    @PostMapping("/records/{id}/joining-report/decision")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeMovementRecordResponse decide(@PathVariable Long id, @Valid @RequestBody JoiningDecisionRequest decision,
                                                   Authentication authentication) {
        Long approverEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return joiningReportService.decide(id, decision, approverEmployeeId);
    }

    /** "Seek Clarification" - sends a PENDING_VERIFICATION joining report back to the employee for amendment. */
    @PostMapping("/records/{id}/joining-report/request-clarification")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public EmployeeMovementRecordResponse requestClarification(@PathVariable Long id, @Valid @RequestBody ClarificationRequest request,
                                                                  Authentication authentication) {
        Long supervisorEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return joiningReportService.requestClarification(id, request.remarks(), supervisorEmployeeId);
    }

    /** ESS: "Amend & Re-submit Report" after a CLARIFICATION_REQUESTED return. */
    @PutMapping("/records/{id}/joining-report/resubmit")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN')")
    public EmployeeMovementRecordResponse resubmitJoiningReport(@PathVariable Long id, @Valid @RequestBody JoiningReportRequest request,
                                                                   Authentication authentication, HttpServletRequest httpRequest) {
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        return joiningReportService.resubmitJoiningReport(id, request, employeeId, httpRequest.getRemoteAddr());
    }

    // ---- Tab 4: Payroll & LPC Clearance ----

    @GetMapping("/payroll-inputs")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public List<PayrollMovementInputResponse> payrollInputsForMonth(@RequestParam int year, @RequestParam int month) {
        return payrollMovementIntegrationService.forMonth(year, month);
    }

    @GetMapping("/records/{id}/payroll-inputs")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public List<PayrollMovementInputResponse> payrollInputsForMovement(@PathVariable Long id) {
        return payrollMovementIntegrationService.forMovement(id);
    }

    @PostMapping("/records/{id}/lpc/accept")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public EmployeeMovementRecordResponse acceptLpc(@PathVariable Long id) {
        return joiningReportService.acceptLpc(id);
    }

    /** "Generate / Download LPC" - idempotent: computes and persists the certificate on first call, just re-renders it on later calls. */
    @GetMapping("/records/{id}/lpc/pdf")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'FINANCE_ADMIN')")
    public ResponseEntity<byte[]> lpcPdf(@PathVariable Long id, Authentication authentication) {
        Long generatedBy = SecurityUtils.currentEmployeeId(authentication);
        return pdfResponse(lastPayCertificateService.generatePdf(id, generatedBy), "LPC_" + id + ".pdf");
    }

    /** ESS: downloads an already-generated LPC for the caller's own record - never triggers first-time generation (that's an HR/Finance action). */
    @GetMapping("/records/{id}/my-documents/lpc-pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myLpcPdf(@PathVariable Long id, Authentication authentication) {
        Long employeeId = requireEmployeeId(authentication);
        return pdfResponse(lastPayCertificateService.generatePdfForEmployee(id, employeeId), "LPC_" + id + ".pdf");
    }
}
