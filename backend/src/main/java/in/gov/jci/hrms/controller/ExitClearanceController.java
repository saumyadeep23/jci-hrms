package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ExitClearanceFinalizeRequest;
import in.gov.jci.hrms.dto.ExitClearanceInitiateRequest;
import in.gov.jci.hrms.dto.ExitClearanceItemResponse;
import in.gov.jci.hrms.dto.ExitClearanceItemUpdateRequest;
import in.gov.jci.hrms.dto.ExitClearanceRequestResponse;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.ExitClearanceService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exit Formalities: multi-department clearance checklist + release order. PIMS Superannuation/Exit workflow. */
@RestController
@RequestMapping("/api/v1/exit-clearances")
public class ExitClearanceController {

    private final ExitClearanceService exitClearanceService;

    public ExitClearanceController(ExitClearanceService exitClearanceService) {
        this.exitClearanceService = exitClearanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ExitClearanceRequestResponse> initiate(@Valid @RequestBody ExitClearanceInitiateRequest request) {
        ExitClearanceRequest created = exitClearanceService.initiateExit(
                request.employeeId(), request.separationType(), request.targetReleaseDate(), request.remarks());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ExitClearanceRequestResponse getById(@PathVariable Long id) {
        return toResponse(exitClearanceService.getRequest(id));
    }

    /** 404 (not an empty body) when the employee has no exit clearance request yet - lets the frontend distinguish "none yet" from "loading". */
    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ExitClearanceRequestResponse getLatestForEmployee(@PathVariable Long employeeId) {
        return exitClearanceService.findLatestForEmployee(employeeId)
                .map(this::toResponse)
                .orElseThrow(() -> new in.gov.jci.hrms.exception.MasterDataNotFoundException("ExitClearanceRequest for employee", employeeId));
    }

    @GetMapping("/{id}/checklist")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public List<ExitClearanceItemResponse> checklist(@PathVariable Long id) {
        return exitClearanceService.checklist(id).stream().map(ExitClearanceItemResponse::from).toList();
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ExitClearanceItemResponse updateClearanceItem(@PathVariable Long itemId,
                                                          @Valid @RequestBody ExitClearanceItemUpdateRequest request,
                                                          Authentication authentication) {
        ExitClearanceItem item = exitClearanceService.updateDepartmentClearance(
                itemId, request.status(), request.duesRecoveryAmount(), request.remarks(), SecurityUtils.currentEmployeeId(authentication));
        return ExitClearanceItemResponse.from(item);
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ExitClearanceRequestResponse finalizeReleaseOrder(@PathVariable Long id, @Valid @RequestBody ExitClearanceFinalizeRequest request) {
        return toResponse(exitClearanceService.finalizeReleaseOrder(id, request.releaseOrderRefNo(), request.releaseOrderDate()));
    }

    private ExitClearanceRequestResponse toResponse(ExitClearanceRequest request) {
        List<ExitClearanceItemResponse> items = exitClearanceService.checklist(request.getId()).stream()
                .map(ExitClearanceItemResponse::from).toList();
        return ExitClearanceRequestResponse.from(request, items);
    }
}
