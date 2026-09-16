package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsClearNoDuesRequest;
import in.gov.jci.hrms.dto.JciEccsSettlementResponse;
import in.gov.jci.hrms.repository.JciEccsSettlementRepository;
import in.gov.jci.hrms.security.SecurityUtils;
import in.gov.jci.hrms.service.JciEccsNoDuesService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * JCIECCS Lifecycle Engine Phase 3 - the cooperative no-dues/settlement position for employee
 * separation. See {@link JciEccsNoDuesService}'s own javadoc for why this integrates with the existing
 * HR exit-clearance workflow (ExitClearanceItem, department JCIECCS) rather than building a second
 * separation engine.
 */
@RestController
@RequestMapping("/api/jcieccs/settlements")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsSettlementController {

    private final JciEccsNoDuesService noDuesService;
    private final JciEccsSettlementRepository settlementRepository;

    public JciEccsSettlementController(JciEccsNoDuesService noDuesService, JciEccsSettlementRepository settlementRepository) {
        this.noDuesService = noDuesService;
        this.settlementRepository = settlementRepository;
    }

    @GetMapping
    public List<JciEccsSettlementResponse> list() {
        return settlementRepository.findAll().stream().map(JciEccsSettlementResponse::from).toList();
    }

    @GetMapping("/{employeeId}")
    public JciEccsSettlementResponse get(@PathVariable Long employeeId, Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return JciEccsSettlementResponse.from(noDuesService.getOrCalculate(employeeId, performedByEmployeeId));
    }

    @PostMapping("/{employeeId}/calculate")
    public JciEccsSettlementResponse calculate(@PathVariable Long employeeId, Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        return JciEccsSettlementResponse.from(noDuesService.calculate(employeeId, performedByEmployeeId));
    }

    // SEC-010 (docs/security/RBAC_MIGRATION_REPORT.md): clearing JCIECCS no-dues is the financial
    // settlement approval action - narrowed off the class-level SUPER_ADMIN grant, keeping only the two
    // functional domain roles the class-level already lists.
    @PostMapping("/{employeeId}/clear")
    @PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN')")
    public JciEccsSettlementResponse clear(@PathVariable Long employeeId, @Valid @RequestBody JciEccsClearNoDuesRequest request,
                                            Authentication authentication) {
        Long performedByEmployeeId = SecurityUtils.currentEmployeeId(authentication);
        noDuesService.clear(employeeId, request.remarks(), performedByEmployeeId);
        return JciEccsSettlementResponse.from(noDuesService.getOrCalculate(employeeId, performedByEmployeeId));
    }
}
