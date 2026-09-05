package in.gov.jci.hrms.audit;

import in.gov.jci.hrms.entity.AuditAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises AuditableEntityListener's callback methods directly, as plain
 * method calls, rather than via a real Hibernate session - there's no live
 * Postgres in this environment to trigger genuine @PostLoad/@PreUpdate JPA
 * lifecycle events (same limitation as EmployeeRepositoryTest). This still
 * covers the listener's own logic (the before/after snapshot bookkeeping
 * and the AuditBeanAccessor bridge) in isolation from JPA/Hibernate.
 */
@ExtendWith(MockitoExtension.class)
class AuditableEntityListenerTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AuditLogRecorder auditLogRecorder;

    private final AuditableEntityListener listener = new AuditableEntityListener();
    private final AuditBeanAccessor accessor = new AuditBeanAccessor();

    @BeforeEach
    void setUp() {
        accessor.setApplicationContext(applicationContext);
    }

    @AfterEach
    void resetStaticContext() {
        accessor.setApplicationContext(null);
    }

    private static class FakeAuditable implements Auditable {
        private final Long id;
        Map<String, Object> snapshot;

        FakeAuditable(Long id, Map<String, Object> snapshot) {
            this.id = id;
            this.snapshot = snapshot;
        }

        @Override
        public String auditEntityName() {
            return "Fake";
        }

        @Override
        public Long auditEntityId() {
            return id;
        }

        @Override
        public Map<String, Object> auditSnapshot() {
            return snapshot;
        }
    }

    @Test
    void onPostPersist_recordsCreateWithNullBefore() {
        when(applicationContext.getBean(AuditLogRecorder.class)).thenReturn(auditLogRecorder);
        FakeAuditable entity = new FakeAuditable(1L, Map.of("name", "Asha"));

        listener.onPostPersist(entity);

        verify(auditLogRecorder).record("Fake", 1L, AuditAction.CREATE, null, Map.of("name", "Asha"));
    }

    @Test
    void postLoadThenPreUpdate_recordsUpdateWithBeforeAndAfterDiff() {
        when(applicationContext.getBean(AuditLogRecorder.class)).thenReturn(auditLogRecorder);
        FakeAuditable entity = new FakeAuditable(2L, Map.of("name", "Asha"));

        listener.onPostLoad(entity);
        entity.snapshot = Map.of("name", "Asha Verma");
        listener.onPreUpdate(entity);

        verify(auditLogRecorder).record("Fake", 2L, AuditAction.UPDATE, Map.of("name", "Asha"), Map.of("name", "Asha Verma"));
    }

    @Test
    void onPreRemove_usesPostLoadSnapshotAsBeforeAndNullAfter() {
        when(applicationContext.getBean(AuditLogRecorder.class)).thenReturn(auditLogRecorder);
        FakeAuditable entity = new FakeAuditable(3L, Map.of("name", "Asha"));

        listener.onPostLoad(entity);
        listener.onPreRemove(entity);

        verify(auditLogRecorder).record("Fake", 3L, AuditAction.DELETE, Map.of("name", "Asha"), null);
    }

    @Test
    void onPreRemove_withoutPriorPostLoad_fallsBackToCurrentSnapshotAsBefore() {
        when(applicationContext.getBean(AuditLogRecorder.class)).thenReturn(auditLogRecorder);
        FakeAuditable entity = new FakeAuditable(4L, Map.of("name", "Ravi"));

        listener.onPreRemove(entity);

        verify(auditLogRecorder).record("Fake", 4L, AuditAction.DELETE, Map.of("name", "Ravi"), null);
    }

    @Test
    void onPostPersist_whenRecorderBeanUnavailable_doesNotThrow() {
        accessor.setApplicationContext(null);
        FakeAuditable entity = new FakeAuditable(5L, Map.of("name", "Asha"));

        listener.onPostPersist(entity);

        verifyNoInteractions(auditLogRecorder);
    }

    @Test
    void nonAuditableEntity_isIgnored() {
        listener.onPostPersist(new Object());
        listener.onPostLoad(new Object());
        listener.onPreUpdate(new Object());
        listener.onPreRemove(new Object());

        verifyNoInteractions(applicationContext, auditLogRecorder);
    }
}
