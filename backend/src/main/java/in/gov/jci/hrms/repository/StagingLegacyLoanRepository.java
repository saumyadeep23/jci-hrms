package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StagingLegacyLoan;
import in.gov.jci.hrms.entity.StagingRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StagingLegacyLoanRepository extends JpaRepository<StagingLegacyLoan, Long> {

    List<StagingLegacyLoan> findByStatus(StagingRowStatus status);

    long countByStatus(StagingRowStatus status);

    Optional<StagingLegacyLoan> findByLoanAccountNumber(String loanAccountNumber);
}
