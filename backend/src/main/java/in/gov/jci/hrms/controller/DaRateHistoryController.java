package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.DaRateHistoryRequest;
import in.gov.jci.hrms.dto.DaRateHistoryResponse;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.service.DaRateHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * GET is open to FINANCE_ADMIN/HR_ADMIN/SUPER_ADMIN (broader - FINANCE_ADMIN
 * is who the frontend route actually gates this page to); POST is HR_ADMIN/
 * SUPER_ADMIN only, matching the master-data mutation pattern used
 * elsewhere in this codebase (e.g. HolidayController).
 */
@RestController
@RequestMapping("/api/da-rates")
public class DaRateHistoryController {

    private final DaRateHistoryService daRateHistoryService;

    public DaRateHistoryController(DaRateHistoryService daRateHistoryService) {
        this.daRateHistoryService = daRateHistoryService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<DaRateHistoryResponse> create(@Valid @RequestBody DaRateHistoryRequest request) {
        DaRateHistoryResponse created = daRateHistoryService.create(request);
        return ResponseEntity.created(URI.create("/api/da-rates/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'HR_ADMIN', 'SUPER_ADMIN')")
    public List<DaRateHistoryResponse> list() {
        return daRateHistoryService.list();
    }

    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'HR_ADMIN', 'SUPER_ADMIN')")
    public DaRateHistoryResponse getCurrent(@RequestParam ScaleType scaleType, @RequestParam LocalDate effectiveDate) {
        return daRateHistoryService.getCurrent(scaleType, effectiveDate);
    }
}
