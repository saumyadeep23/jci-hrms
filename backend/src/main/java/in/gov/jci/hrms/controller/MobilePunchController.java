package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.MobilePunchRequest;
import in.gov.jci.hrms.dto.MobilePunchResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
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

    public MobilePunchController(MobilePunchService mobilePunchService) {
        this.mobilePunchService = mobilePunchService;
    }

    /**
     * Any authenticated staff member can punch their own attendance,
     * regardless of which admin-tier role(s) their token also carries - a
     * FINANCE_ADMIN or SUPER_ADMIN is still a person who shows up to work.
     * employeeId is resolved from the JWT's employee_id claim when the ESS
     * mobile client omits it from the request body; an explicit employeeId
     * in the body (e.g. from an HR tool) is honored as-is.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MobilePunchResponse> create(@Valid @RequestBody MobilePunchRequest request, Authentication authentication) {
        MobilePunchResponse created = mobilePunchService.create(resolveEmployeeId(request, authentication));
        return ResponseEntity.created(URI.create("/api/attendance/punch/" + created.id())).body(created);
    }

    private MobilePunchRequest resolveEmployeeId(MobilePunchRequest request, Authentication authentication) {
        if (request.employeeId() != null) {
            return request;
        }
        Long employeeId = SecurityUtils.currentEmployeeId(authentication);
        if (employeeId == null) {
            throw new BusinessRuleViolationException(
                    "employeeId was not supplied and could not be resolved from the token's employee_id claim");
        }
        return new MobilePunchRequest(employeeId, request.punchTime(), request.punchType(), request.latitude(),
                request.longitude(), request.accuracyMeters(), request.deviceId(), request.photoS3Key());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public MobilePunchResponse getById(@PathVariable Long id) {
        return mobilePunchService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public Page<MobilePunchResponse> list(Pageable pageable) {
        return mobilePunchService.list(pageable);
    }
}
