package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.BaselineTakeOnRequest;
import in.gov.jci.hrms.dto.BaselineTakeOnResponse;
import in.gov.jci.hrms.service.LeaveBaselineTakeOnService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PIMS ALMS Phase 2, Section 1. "HR_SUPER_ADMIN" (as named in the spec) does
 * not exist as a role anywhere in this codebase - roles are opaque JWT
 * strings, and the established convention for this class of admin-only
 * leave/attendance endpoint is hasRole('HR_ADMIN')
 * (see e.g. RegionalOfficeController, DpcController), reused here.
 */
@RestController
@RequestMapping("/api/v1/admin/leave/baseline-takeon")
@PreAuthorize("hasRole('HR_ADMIN')")
public class LeaveBaselineTakeOnController {

    private final LeaveBaselineTakeOnService baselineTakeOnService;

    public LeaveBaselineTakeOnController(LeaveBaselineTakeOnService baselineTakeOnService) {
        this.baselineTakeOnService = baselineTakeOnService;
    }

    @PostMapping
    public ResponseEntity<BaselineTakeOnResponse> takeOn(@Valid @RequestBody BaselineTakeOnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(baselineTakeOnService.takeOn(request));
    }
}
