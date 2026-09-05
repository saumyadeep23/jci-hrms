package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacySalaryHead;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StagingLegacySalaryHeadRepository extends JpaRepository<StagingLegacySalaryHead, Long> {

    List<StagingLegacySalaryHead> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);

    List<StagingLegacySalaryHead> findByStagingSalaryMonthId(Long stagingSalaryMonthId);
}
