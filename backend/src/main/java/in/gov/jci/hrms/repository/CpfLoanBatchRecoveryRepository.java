package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanBatchRecovery;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CpfLoanBatchRecoveryRepository extends JpaRepository<CpfLoanBatchRecovery, Long> {

    /** The idempotency check itself (Part 24) - if a row already exists for this (loan, batch, phase), the recovery has already been resolved and must not be posted again. */
    Optional<CpfLoanBatchRecovery> findByLoan_IdAndPayrollBatch_IdAndRecoveryPhase(Long loanId, Long payrollBatchId, CpfLoanRecoveryPhase recoveryPhase);
}
