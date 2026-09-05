package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ContractualEngagement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContractualEngagementRepository extends JpaRepository<ContractualEngagement, Long> {

    Optional<ContractualEngagement> findByEmployeeIdAndCurrentTrue(Long employeeId);
}
