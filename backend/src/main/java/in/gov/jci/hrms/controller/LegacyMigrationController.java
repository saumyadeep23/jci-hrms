package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.LegacyMigrationStatusResponse;
import in.gov.jci.hrms.dto.SalaryHistoryEntryResponse;
import in.gov.jci.hrms.dto.StageLeaveBalancesRequest;
import in.gov.jci.hrms.dto.StageLoansRequest;
import in.gov.jci.hrms.dto.StageSalaryHistoryRequest;
import in.gov.jci.hrms.dto.StageServiceBookRequest;
import in.gov.jci.hrms.dto.StagingAcknowledgmentResponse;
import in.gov.jci.hrms.dto.ValidateAndPromoteRequest;
import in.gov.jci.hrms.service.LegacyMigrationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/legacy-migration")
@PreAuthorize("hasRole('HR_ADMIN')")
public class LegacyMigrationController {

    private final LegacyMigrationService legacyMigrationService;

    public LegacyMigrationController(LegacyMigrationService legacyMigrationService) {
        this.legacyMigrationService = legacyMigrationService;
    }

    @PostMapping("/stage/leave-balances")
    public StagingAcknowledgmentResponse stageLeaveBalances(@Valid @RequestBody StageLeaveBalancesRequest request) {
        return legacyMigrationService.stageLeaveBalances(request);
    }

    @PostMapping("/stage/loans")
    public StagingAcknowledgmentResponse stageLoans(@Valid @RequestBody StageLoansRequest request) {
        return legacyMigrationService.stageLoans(request);
    }

    @PostMapping("/stage/salary-history")
    public StagingAcknowledgmentResponse stageSalaryHistory(@Valid @RequestBody StageSalaryHistoryRequest request) {
        return legacyMigrationService.stageSalaryHistory(request);
    }

    @PostMapping("/stage/service-book")
    public StagingAcknowledgmentResponse stageServiceBook(@Valid @RequestBody StageServiceBookRequest request) {
        return legacyMigrationService.stageServiceBook(request);
    }

    @PostMapping("/validate-and-promote")
    public LegacyMigrationStatusResponse validateAndPromote(@Valid @RequestBody ValidateAndPromoteRequest request) {
        return legacyMigrationService.validateAndPromote(request);
    }

    @GetMapping("/status")
    public LegacyMigrationStatusResponse status() {
        return legacyMigrationService.getStatus();
    }

    @GetMapping("/salary-history/{employeeId}")
    @PreAuthorize("hasRole('HR_ADMIN') or @employeeSecurity.isSelf(authentication, #employeeId)")
    public List<SalaryHistoryEntryResponse> salaryHistory(@PathVariable Long employeeId) {
        return legacyMigrationService.getSalaryHistory(employeeId);
    }
}
