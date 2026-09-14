package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfRuleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfRuleAuditLogRepository extends JpaRepository<CpfRuleAuditLog, UUID> {

    List<CpfRuleAuditLog> findByVersion_IdOrderByCreatedAtDesc(UUID versionId);

    List<CpfRuleAuditLog> findAllByOrderByCreatedAtDesc();
}
