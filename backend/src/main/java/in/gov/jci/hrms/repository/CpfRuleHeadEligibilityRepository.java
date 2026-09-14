package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfRuleHeadEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CpfRuleHeadEligibilityRepository extends JpaRepository<CpfRuleHeadEligibility, UUID> {

    List<CpfRuleHeadEligibility> findByDetail_IdAndEligibleTrueOrderByDebitPriorityAsc(UUID detailId);

    List<CpfRuleHeadEligibility> findByDetail_Id(UUID detailId);
}
