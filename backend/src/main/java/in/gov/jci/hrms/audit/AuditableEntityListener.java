package in.gov.jci.hrms.audit;

import in.gov.jci.hrms.entity.AuditAction;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registered via @EntityListeners on Employee/EmployeeDependent/
 * EmployeeNominee. JPA instantiates this class itself (it is not a Spring
 * bean), so it resolves AuditLogRecorder through AuditBeanAccessor rather
 * than constructor injection.
 *
 * UPDATE events need a "before" snapshot to diff against, which plain
 * @PreUpdate doesn't provide - by the time it fires, the entity already
 * holds its new in-memory values. @PostLoad stashes a snapshot taken right
 * after the entity is read from the DB; @PreUpdate/@PreRemove consume it.
 * The stash is a per-thread IdentityHashMap keyed by entity instance, since
 * a Hibernate Session - and so the @PostLoad -> @PreUpdate sequence for one
 * entity instance - is confined to a single thread.
 *
 * Note: none of the three audited entities are ever hard-deleted by this
 * app's services today (delete() just flips deletedAt/status and lets
 * dirty-checking issue an UPDATE), so in practice a soft-delete shows up
 * as an UPDATE audit row, not a DELETE one. onPreRemove exists for
 * completeness / a future hard-delete path, but isn't exercised yet.
 */
public class AuditableEntityListener {

    private static final ThreadLocal<Map<Object, Map<String, Object>>> LOADED_SNAPSHOTS =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @PostLoad
    public void onPostLoad(Object entity) {
        if (entity instanceof Auditable auditable) {
            LOADED_SNAPSHOTS.get().put(entity, auditable.auditSnapshot());
        }
    }

    @PostPersist
    public void onPostPersist(Object entity) {
        if (entity instanceof Auditable auditable) {
            record(auditable, AuditAction.CREATE, null, auditable.auditSnapshot());
        }
    }

    @PreUpdate
    public void onPreUpdate(Object entity) {
        if (entity instanceof Auditable auditable) {
            Map<String, Object> before = LOADED_SNAPSHOTS.get().remove(entity);
            record(auditable, AuditAction.UPDATE, before, auditable.auditSnapshot());
        }
    }

    @PreRemove
    public void onPreRemove(Object entity) {
        if (entity instanceof Auditable auditable) {
            Map<String, Object> before = LOADED_SNAPSHOTS.get().remove(entity);
            if (before == null) {
                before = auditable.auditSnapshot();
            }
            record(auditable, AuditAction.DELETE, before, null);
        }
    }

    private void record(Auditable auditable, AuditAction action, Map<String, Object> before, Map<String, Object> after) {
        AuditLogRecorder recorder = AuditBeanAccessor.getBean(AuditLogRecorder.class);
        if (recorder == null) {
            return;
        }
        recorder.record(auditable.auditEntityName(), auditable.auditEntityId(), action, before, after);
    }
}
