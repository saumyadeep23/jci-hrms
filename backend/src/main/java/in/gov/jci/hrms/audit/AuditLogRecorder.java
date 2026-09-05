package in.gov.jci.hrms.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.entity.AuditAction;
import in.gov.jci.hrms.entity.AuditLog;
import in.gov.jci.hrms.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditLogRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRecorder.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogRecorder(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String entityName, Long entityId, AuditAction action,
                        Map<String, Object> before, Map<String, Object> after) {
        AuditLog auditLog = new AuditLog(
                entityName, entityId, action,
                AuditActor.currentUsername(), AuditActor.currentClientIp(),
                toJson(before), toJson(after)
        );
        auditLogRepository.save(auditLog);
    }

    private String toJson(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit snapshot; storing null instead", ex);
            return null;
        }
    }
}
