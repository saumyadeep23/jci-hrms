package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CpfWithdrawalRuleDetailRepository extends JpaRepository<CpfWithdrawalRuleDetail, UUID> {

    Optional<CpfWithdrawalRuleDetail> findByVersion_Id(UUID versionId);
}
