package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.MobilePunchRequest;
import in.gov.jci.hrms.dto.MobilePunchResponse;
import in.gov.jci.hrms.security.AttendanceAggregationSecurity;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.MobilePunchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/attendance/punch")
public class MobilePunchController {

    private final MobilePunchService mobilePunchService;
    private final AttendanceAggregationSecurity attendanceAggSec;

    public MobilePunchController(MobilePunchService mobilePunchService, AttendanceAggregationSecurity attendanceAggSec) {
        this.mobilePunchService = mobilePunchService;
        this.attendanceAggSec = attendanceAggSec;
    }

    /**
     * SEC-002 remediation (docs/security/SEC_001_002_REMEDIATION.md): a
     * caller may only ever punch their OWN attendance - the server derives
     * identity from the JWT's employee_id claim (SecurityUtils.currentEmployeeId),
     * never trusting a client-supplied employeeId as-is. An explicit
     * employeeId in the body targeting someone else is honored only for a
     * caller in HR_ADMIN/SUPER_ADMIN (the "HR tool punches on someone's
     * behalf" case this endpoint's javadoc always described, now actually
     * enforced), reusing this codebase's existing self-or-HR/admin gate
     * (@attendanceAggSec, already used the same way by
     * AttendanceAggregationController and LeaveLedgerEntryController) rather
     * than inventing a new ownership mechanism just for this endpoint.
     * MobilePunchService.create() independently re-derives and re-checks the
     * same rule from its own two parameters below - defense in depth so a
     * future caller of that service method through some other API path
     * cannot silently bypass ownership.
     */
    @PostMapping
    @PreAuthorize("@attendanceAggSec.canEvaluateFor(authentication, #request.employeeId())")
    public ResponseEntity<MobilePunchResponse> create(@Valid @RequestBody MobilePunchRequest request, Authentication authentication) {
        Long callerEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        boolean onBehalfOfOthersPermitted = attendanceAggSec.canActOnBehalfOfOthers(authentication);
        MobilePunchResponse created = mobilePunchService.create(request, callerEmployeeId, onBehalfOfOthersPermitted);
        return ResponseEntity.created(URI.create("/api/attendance/punch/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('HR_ADMIN')")
    public MobilePunchResponse getById(@PathVariable Long id) {
        return mobilePunchService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public Page<MobilePunchResponse> list(Pageable pageable) {
        return mobilePunchService.list(pageable);
    }
}
