package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DoaResolutionResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.service.DoaResolverService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PIMS ALMS Phase 2, Section 6 - diagnostic/admin lookup for the DOA Dynamic Resolver, independent of any particular submit flow. */
@RestController
@RequestMapping("/api/v1/admin/doa")
@PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
public class DoaResolverController {

    private final DoaResolverService doaResolverService;

    public DoaResolverController(DoaResolverService doaResolverService) {
        this.doaResolverService = doaResolverService;
    }

    @GetMapping("/resolve/{employeeId}")
    public DoaResolutionResponse resolve(@PathVariable Long employeeId) {
        return doaResolverService.resolveApprover(employeeId)
                .map(resolution -> new DoaResolutionResponse(
                        resolution.employee().getId(), resolution.employee().getEmployeeCode(),
                        resolution.post().getId(), resolution.post().getTitle(), resolution.routingReason()))
                .orElseThrow(() -> new BusinessRuleViolationException("No approver could be resolved for employee " + employeeId));
    }
}
