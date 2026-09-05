package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CombinedLeaveApplicationRequest;
import in.gov.jci.hrms.dto.CombinedLeaveApplicationResponse;
import in.gov.jci.hrms.service.CombinedLeaveApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PIMS ALMS Phase 2, Section 3 - creates a linked CL+RH pair. A separate
 * controller/path from LeaveApplicationController (not an added method on
 * it) so its existing, already-tested endpoints are untouched; each leg
 * still goes through the normal /api/leave-applications/{id}/submit etc.
 * individually once created here.
 */
@RestController
@RequestMapping("/api/leave-applications/combined")
@PreAuthorize("hasAnyRole('EMPLOYEE', 'HR_ADMIN', 'SUPER_ADMIN')")
public class CombinedLeaveController {

    private final CombinedLeaveApplicationService combinedLeaveApplicationService;

    public CombinedLeaveController(CombinedLeaveApplicationService combinedLeaveApplicationService) {
        this.combinedLeaveApplicationService = combinedLeaveApplicationService;
    }

    @PostMapping
    public ResponseEntity<CombinedLeaveApplicationResponse> create(@Valid @RequestBody CombinedLeaveApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(combinedLeaveApplicationService.create(request));
    }
}
