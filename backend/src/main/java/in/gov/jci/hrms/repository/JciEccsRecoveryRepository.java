package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.JciEccsRecovery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JciEccsRecoveryRepository extends JpaRepository<JciEccsRecovery, Long> {

    /** The idempotency backstop (V90 UNIQUE constraint) - a retried payroll callback line or a
     * duplicate cash-repayment submission resolves to the same already-created recovery instead of
     * creating another one. */
    Optional<JciEccsRecovery> findByIdempotencyKey(String idempotencyKey);

    /** Phase 5 concurrency hardening: two concurrent reversal requests for the same recovery must
     * serialize on this row so the second observes the already-REVERSED status after the first commits,
     * failing cleanly with a business exception instead of racing into the REVERSAL: idempotency key's
     * unique constraint (which aborts the whole Postgres transaction rather than degrading gracefully). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM JciEccsRecovery r WHERE r.id = :id")
    Optional<JciEccsRecovery> findByIdForUpdate(@Param("id") Long id);

    List<JciEccsRecovery> findByMember_IdOrderByCreatedAtDesc(Long memberId);

    List<JciEccsRecovery> findByLoan_IdOrderByCreatedAtDesc(Long loanId);

    /** The priority-cascade audit trail's default view (spec section 66) - most recent recoveries across
     * the whole module, bounded so the page never loads an unbounded history into the browser. */
    List<JciEccsRecovery> findTop100ByOrderByCreatedAtDesc();
}
