package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsMigrationStatusResponse;
import in.gov.jci.hrms.dto.JciEccsStagingMemberRequest;
import in.gov.jci.hrms.service.JciEccsHistoricalMigrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Historical Migration Service (spec section 4.6) - not part of the spec's own REST endpoint list
 * (section 5), but needs an entry point the same way legacy-migration's stage/validate-and-promote does. */
@RestController
@RequestMapping("/api/jcieccs/migration")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'SUPER_ADMIN')")
public class JciEccsMigrationController {

    private final JciEccsHistoricalMigrationService migrationService;

    public JciEccsMigrationController(JciEccsHistoricalMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @PostMapping("/stage/members")
    public ResponseEntity<Void> stageMembers(@Valid @RequestBody List<JciEccsStagingMemberRequest> requests) {
        migrationService.stage(requests);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/validate-and-promote")
    public JciEccsMigrationStatusResponse validateAndPromote() {
        migrationService.validateAndPromote();
        return migrationService.getStatus();
    }

    @GetMapping("/status")
    public JciEccsMigrationStatusResponse status() {
        return migrationService.getStatus();
    }
}
