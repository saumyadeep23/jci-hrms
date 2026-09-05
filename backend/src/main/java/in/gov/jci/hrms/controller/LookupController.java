package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.IfscLookupResponse;
import in.gov.jci.hrms.dto.PincodeLookupResponse;
import in.gov.jci.hrms.service.IfscLookupService;
import in.gov.jci.hrms.service.PincodeLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookups")
public class LookupController {

    private final PincodeLookupService pincodeLookupService;
    private final IfscLookupService ifscLookupService;

    public LookupController(PincodeLookupService pincodeLookupService, IfscLookupService ifscLookupService) {
        this.pincodeLookupService = pincodeLookupService;
        this.ifscLookupService = ifscLookupService;
    }

    @GetMapping("/pincode/{pincode}")
    public PincodeLookupResponse pincode(@PathVariable String pincode) {
        return pincodeLookupService.lookup(pincode);
    }

    @GetMapping("/ifsc/{ifsc}")
    public IfscLookupResponse ifsc(@PathVariable String ifsc) {
        return ifscLookupService.lookup(ifsc);
    }
}
