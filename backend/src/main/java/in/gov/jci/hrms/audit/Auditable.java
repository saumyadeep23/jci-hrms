package in.gov.jci.hrms.audit;

import java.util.Map;

/**
 * Implemented by entities that AuditableEntityListener should record
 * CREATE/UPDATE/DELETE events for. auditSnapshot() must only use fields
 * and to-one association IDs (e.g. department.getId()) that are safe to
 * read without triggering a lazy-load - never a full lazy association
 * object, which could throw LazyInitializationException or silently
 * force an extra query depending on session state.
 */
public interface Auditable {

    String auditEntityName();

    Long auditEntityId();

    Map<String, Object> auditSnapshot();
}
