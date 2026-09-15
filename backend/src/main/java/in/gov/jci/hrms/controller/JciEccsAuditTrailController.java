package in.gov.jci.hrms.controller;

import in.gov.jci.hrms.dto.JciEccsAuditEventResponse;
import in.gov.jci.hrms.repository.JciEccsLifecycleEventRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 5 (spec sections 22, 29 - Operational Traceability): read-only access to the generic JCIECCS
 * lifecycle audit trail (JciEccsLifecycleEvent, V87) per entity, so an administrator investigating a
 * loan/member/recovery/reconciliation/collection-batch can see its full history (actor, timestamp,
 * old/new value, reference, remarks) from its own detail screen. The repository query this exposes
 * (findByEntityTypeAndEntityIdOrderByEventDateAsc) already existed but had no controller and no supporting
 * index until now (V93) - every JCIECCS service already writes to this table via JciEccsLifecycleEventService,
 * nothing new is recorded here.
 */
@RestController
@RequestMapping("/api/jcieccs/audit-events")
@PreAuthorize("hasAnyRole('COOP_ADMIN', 'FINANCE_ADMIN', 'SUPER_ADMIN')")
public class JciEccsAuditTrailController {

    private final JciEccsLifecycleEventRepository lifecycleEventRepository;

    public JciEccsAuditTrailController(JciEccsLifecycleEventRepository lifecycleEventRepository) {
        this.lifecycleEventRepository = lifecycleEventRepository;
    }

    /** entityType matches whatever JciEccsLifecycleEventService.record was called with for that kind of
     * entity - e.g. JCIECCS_LOAN, JCIECCS_RECOVERY, JCIECCS_MEMBER, JCIECCS_COLLECTION_BATCH,
     * JCIECCS_RECONCILIATION. */
    @GetMapping("/{entityType}/{entityId}")
    public List<JciEccsAuditEventResponse> forEntity(@PathVariable String entityType, @PathVariable Long entityId) {
        return lifecycleEventRepository.findByEntityTypeAndEntityIdOrderByEventDateAsc(entityType, entityId)
                .stream().map(JciEccsAuditEventResponse::from).toList();
    }
}
