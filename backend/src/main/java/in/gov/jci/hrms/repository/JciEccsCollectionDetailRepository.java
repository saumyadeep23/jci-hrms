package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsCollectionDetail;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JciEccsCollectionDetailRepository extends JpaRepository<JciEccsCollectionDetail, Long> {

    List<JciEccsCollectionDetail> findByBatch_IdOrderByEmployeeIdAsc(Long batchId);

    Optional<JciEccsCollectionDetail> findByBatch_IdAndEmployeeId(Long batchId, Long employeeId);

    /** Phase 5 concurrency hardening: two identical/duplicate debit-confirmation callbacks for the same
     * batch+employee line must serialize on this row rather than both observing PENDING_DEBIT and racing
     * into JciEccsRecoveryService with the same idempotency key (which would otherwise surface as a raw,
     * transaction-aborting unique-constraint violation on Postgres rather than a clean no-op). The second
     * caller blocks until the first commits, then re-reads the now-updated debitStatus and skips. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM JciEccsCollectionDetail d WHERE d.batch.id = :batchId AND d.employeeId = :employeeId")
    Optional<JciEccsCollectionDetail> findByBatch_IdAndEmployeeIdForUpdate(@Param("batchId") Long batchId, @Param("employeeId") Long employeeId);

    /** Used by the payroll recovery resolver - the collection_batch for a given payrollRunId may not
     * exist yet (JCIECCS snapshot not requested before Payroll calculates), in which case this returns
     * empty and the resolver contributes zero to that employee's deductions for this run. */
    Optional<JciEccsCollectionDetail> findByBatch_PayrollRunIdAndEmployeeId(String payrollRunId, Long employeeId);

    /** Phase 1 cash-vs-locked-snapshot check (JciEccsLoanService.postCashRepayment): a LOCKED batch whose
     * debit for this loan hasn't been confirmed yet (still PENDING_DEBIT) is the exact window where a cash
     * repayment risks double recovery once Payroll's callback eventually arrives - these rows must be
     * flagged RECONCILIATION_REQUIRED, never silently amended. */
    @Query("SELECT d FROM JciEccsCollectionDetail d WHERE (d.termLoan.id = :loanId OR d.emergencyLoan.id = :loanId) "
            + "AND d.batch.batchStatus = in.gov.jci.hrms.entity.JciEccsCollectionBatchStatus.LOCKED "
            + "AND d.debitStatus = in.gov.jci.hrms.entity.JciEccsDebitStatus.PENDING_DEBIT")
    List<JciEccsCollectionDetail> findLockedPendingByLoanId(@Param("loanId") Long loanId);
}
