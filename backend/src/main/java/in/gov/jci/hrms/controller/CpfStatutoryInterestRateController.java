package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.CpfStatutoryInterestRateRequest;
import in.gov.jci.hrms.dto.CpfStatutoryInterestRateResponse;
import in.gov.jci.hrms.service.CpfStatutoryInterestRateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * CPF Rate of Interest Entry - the notification master CpfRateResolutionService's Para 60(2) resolution
 * reads from (see CpfTrustPassbookService and CpfInterestComputationService.crystallizeInterimInterest()).
 * GET is open to the same roles as CpfTrustController generally (FINANCE_ADMIN/CPF_ADMIN/SUPER_ADMIN); POST
 * is narrower - CPF_ADMIN/SUPER_ADMIN only - matching the read-vs-write role split DaRateHistoryController
 * uses for DA rates (HR_ADMIN owns that mutation; CPF_ADMIN owns this one, since notifying a statutory CPF
 * rate is the Trust's own act, not general finance's).
 */
@RestController
@RequestMapping("/api/v1/payroll/trust/interest/rates")
public class CpfStatutoryInterestRateController {

    private final CpfStatutoryInterestRateService rateService;

    public CpfStatutoryInterestRateController(CpfStatutoryInterestRateService rateService) {
        this.rateService = rateService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CPF_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CpfStatutoryInterestRateResponse> create(@Valid @RequestBody CpfStatutoryInterestRateRequest request) {
        CpfStatutoryInterestRateResponse created = rateService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/payroll/trust/interest/rates/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'CPF_ADMIN', 'SUPER_ADMIN')")
    public List<CpfStatutoryInterestRateResponse> list() {
        return rateService.list();
    }
}
