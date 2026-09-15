-- JCIECCS Lifecycle Engine Phase 5 hardening (spec sections 24, 29 - Operational Traceability):
-- JciEccsLifecycleEventRepository.findByEntityTypeAndEntityIdOrderByEventDateAsc already existed (V87) but
-- had no supporting index, forcing a sequential scan of the whole audit table on every "show me this
-- entity's history" lookup - exactly the traceability query path an administrator investigating a loan/
-- recovery/reconciliation exception relies on. Additive only; never touches V87-V92.
CREATE INDEX idx_jcieccs_lifecycle_event_entity ON jcieccs_lifecycle_event (entity_type, entity_id, event_date);
