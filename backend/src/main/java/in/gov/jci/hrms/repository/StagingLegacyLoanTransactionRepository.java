package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacyLoanTransaction;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StagingLegacyLoanTransactionRepository extends JpaRepository<StagingLegacyLoanTransaction, Long> {

    List<StagingLegacyLoanTransaction> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);

    List<StagingLegacyLoanTransaction> findByStagingLoanId(Long stagingLoanId);
}
