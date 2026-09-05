package in.gov.jci.hrms.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.AuditLog;
import in.gov.jci.hrms.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogRecorderTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogRecorder auditLogRecorder;

    @BeforeEach
    void setUp() {
        auditLogRecorder = new AuditLogRecorder(auditLogRepository, new ObjectMapper());
    }

    @Test
    void record_forCreate_savesLogWithNullBeforeAndSerializedAfter() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("employeeCode", "EMP-001");
        after.put("status", "ACTIVE");

        auditLogRecorder.record("Employee", 1L, AuditAction.CREATE, null, after);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getEntityName()).isEqualTo("Employee");
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).contains("\"employeeCode\":\"EMP-001\"").contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void record_forUpdate_savesBothBeforeAndAfterAsJson() {
        Map<String, Object> before = Map.of("status", "ACTIVE");
        Map<String, Object> after = Map.of("status", "TERMINATED");

        auditLogRecorder.record("Employee", 2L, AuditAction.UPDATE, before, after);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getBeforeState()).contains("\"status\":\"ACTIVE\"");
        assertThat(saved.getAfterState()).contains("\"status\":\"TERMINATED\"");
    }

    @Test
    void record_forDelete_savesNullAfterState() {
        auditLogRecorder.record("EmployeeDependent", 3L, AuditAction.DELETE, Map.of("name", "Ravi"), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo(AuditAction.DELETE);
        assertThat(saved.getAfterState()).isNull();
        assertThat(saved.getBeforeState()).contains("Ravi");
    }
}
