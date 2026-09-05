package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacyLeaveBalance;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StagingLegacyLeaveBalanceRepository extends JpaRepository<StagingLegacyLeaveBalance, Long> {

    List<StagingLegacyLeaveBalance> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);
}
