package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.IfscLookupResponse;
import in.gov.jci.hrms.service.IfscLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PIMS_SPEC.md route aliases: GET /api/v1/finance/ifsc/:ifsc and
 * GET /api/v1/masters/ifsc/:ifscCode. Same IfscLookupService/response shape
 * as LookupController's /api/lookups/ifsc/{ifsc} (kept unchanged for
 * backward compatibility with existing frontend callers) - this controller
 * exists purely so new callers can use the spec's standardized paths.
 */
@RestController
@RequestMapping({"/api/v1/finance", "/api/v1/masters"})
public class FinanceLookupController {

    private final IfscLookupService ifscLookupService;

    public FinanceLookupController(IfscLookupService ifscLookupService) {
        this.ifscLookupService = ifscLookupService;
    }

    @GetMapping("/ifsc/{ifsc}")
    public IfscLookupResponse ifsc(@PathVariable String ifsc) {
        return ifscLookupService.lookup(ifsc);
    }
}
