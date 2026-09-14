package in.gov.jci.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.entity.JciEccsLifecycleEvent;
import in.gov.jci.hrms.repository.JciEccsLifecycleEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every mutating JCIECCS service call writes one row here (loan create/restructure/top-up/close,
 * repayment posted, debit confirmed/failed, reversal) - the free-form audit trail spec section 3.12/6
 * requires. old_value/new_value are serialized the same way CpfApplicationService.toJson() serializes
 * calculation_trace - best-effort, "{}" fallback rather than failing the whole business transaction over
 * an audit-logging hiccup. performed_by is BIGINT (an employees.id) at the DB level, not a username
 * string - a caller with only a username (no resolved Employee) should pass null and fold the username
 * into remarks instead of losing the actor entirely.
 */
@Service
public class JciEccsLifecycleEventService {

    private final JciEccsLifecycleEventRepository repository;
    private final ObjectMapper objectMapper;

    public JciEccsLifecycleEventService(JciEccsLifecycleEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String entityType, Long entityId, String eventType, Object oldValue, Object newValue,
                        String referenceId, Long performedByEmployeeId, String remarks) {
        repository.save(new JciEccsLifecycleEvent(entityType, entityId, eventType, toJson(oldValue), toJson(newValue),
                referenceId, performedByEmployeeId, remarks));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
