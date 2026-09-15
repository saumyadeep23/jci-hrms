package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsRecoveryResponse;
import in.gov.jci.hrms.entity.JciEccsRecovery;
import in.gov.jci.hrms.entity.JciEccsRecoveryAllocation;
import in.gov.jci.hrms.repository.JciEccsRecoveryAllocationRepository;
import in.gov.jci.hrms.repository.JciEccsRecoveryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The priority-cascade recovery/allocation audit trail (spec section 66) - read-only over the Phase 1/2
 * jcieccs_recovery / jcieccs_recovery_allocation tables. Never a second event log; jcieccs_lifecycle_event
 * remains the generic audit trail, this is the business-specific "what got recovered and how was it split"
 * view the frontend's AuditTrailPage needs.
 */
@RestController
@RequestMapping("/api/jcieccs/recoveries")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsRecoveryController {

    private final JciEccsRecoveryRepository recoveryRepository;
    private final JciEccsRecoveryAllocationRepository allocationRepository;

    public JciEccsRecoveryController(JciEccsRecoveryRepository recoveryRepository, JciEccsRecoveryAllocationRepository allocationRepository) {
        this.recoveryRepository = recoveryRepository;
        this.allocationRepository = allocationRepository;
    }

    /** Phase 5 hardening: allocations for the whole page are fetched in ONE query (grouped here by
     * recovery id) instead of one query per recovery row - a real N+1 on this exact page (up to 100 extra
     * queries per load), not a speculative one. */
    @GetMapping
    public List<JciEccsRecoveryResponse> list(@RequestParam(required = false) Long memberId, @RequestParam(required = false) Long loanId) {
        List<JciEccsRecovery> recoveries;
        if (loanId != null) {
            recoveries = recoveryRepository.findByLoan_IdOrderByCreatedAtDesc(loanId);
        } else if (memberId != null) {
            recoveries = recoveryRepository.findByMember_IdOrderByCreatedAtDesc(memberId);
        } else {
            recoveries = recoveryRepository.findTop100ByOrderByCreatedAtDesc();
        }
        Map<Long, List<JciEccsRecoveryAllocation>> allocationsByRecoveryId =
                allocationRepository.findByRecovery_IdIn(recoveries.stream().map(JciEccsRecovery::getId).toList())
                        .stream().collect(Collectors.groupingBy(a -> a.getRecovery().getId()));
        return recoveries.stream()
                .map(r -> JciEccsRecoveryResponse.from(r, allocationsByRecoveryId.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @GetMapping("/{id}")
    public JciEccsRecoveryResponse get(@PathVariable Long id) {
        JciEccsRecovery recovery = recoveryRepository.findById(id)
                .orElseThrow(() -> new in.gov.jci.hrms.exception.MasterDataNotFoundException("JCIECCS Recovery", id));
        return JciEccsRecoveryResponse.from(recovery, allocationRepository.findByRecovery_IdOrderByAllocationSequenceAsc(id));
    }
}
