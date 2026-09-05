package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.ServiceBookEventResponse;
import in.gov.jci.hrms.service.LegacyMigrationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/service-book")
public class ServiceBookController {

    private final LegacyMigrationService legacyMigrationService;

    public ServiceBookController(LegacyMigrationService legacyMigrationService) {
        this.legacyMigrationService = legacyMigrationService;
    }

    @GetMapping("/{employeeId}/timeline")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<ServiceBookEventResponse> timeline(@PathVariable Long employeeId) {
        return legacyMigrationService.getServiceBookTimeline(employeeId);
    }
}
