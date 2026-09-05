package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacySalaryMonth;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StagingLegacySalaryMonthRepository extends JpaRepository<StagingLegacySalaryMonth, Long> {

    List<StagingLegacySalaryMonth> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);
}
