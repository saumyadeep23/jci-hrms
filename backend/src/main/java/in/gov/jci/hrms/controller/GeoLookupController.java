package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.PincodeLookupResponse;
import in.gov.jci.hrms.service.PincodeLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PIMS_SPEC.md route alias: GET /api/v1/geo/pincode/:pincode. Same
 * PincodeLookupService/response shape as LookupController's
 * /api/lookups/pincode/{pincode} (kept unchanged for backward
 * compatibility with existing frontend callers) - this controller exists
 * purely so new callers can use the spec's standardized path.
 */
@RestController
@RequestMapping("/api/v1/geo")
public class GeoLookupController {

    private final PincodeLookupService pincodeLookupService;

    public GeoLookupController(PincodeLookupService pincodeLookupService) {
        this.pincodeLookupService = pincodeLookupService;
    }

    @GetMapping("/pincode/{pincode}")
    public PincodeLookupResponse pincode(@PathVariable String pincode) {
        return pincodeLookupService.lookup(pincode);
    }
}
