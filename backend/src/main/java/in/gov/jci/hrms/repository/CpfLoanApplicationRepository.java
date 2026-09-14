package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpfLoanApplicationRepository extends JpaRepository<CpfLoanApplication, Long> {

    List<CpfLoanApplication> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Source-of-origin lookup (Part 7/41) and the bridge's own idempotency check (Part 6): a second disburse() retry for the same CpfApplication must find the loan already created here rather than making another one. */
    Optional<CpfLoanApplication> findByCpfApplicationId(UUID cpfApplicationId);

    /**
     * Pessimistic row lock (Part 21/22) - "SELECT ... FOR UPDATE" via JPA, taken before cash
     * settlement or payroll-recovery posting reads/mutates this loan's outstanding balance, so a
     * concurrent settlement and payroll recovery against the same loan serialize on this row rather
     * than both reading the same stale outstanding figure.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM CpfLoanApplication l WHERE l.id = :id")
    Optional<CpfLoanApplication> findByIdForUpdate(Long id);

    List<CpfLoanApplication> findByEmployeeIdAndStatus(Long employeeId, CpfLoanApplicationStatus status);

    List<CpfLoanApplication> findByStatusInOrderByCreatedAtDesc(List<CpfLoanApplicationStatus> statuses);

    Page<CpfLoanApplication> findByStatus(CpfLoanApplicationStatus status, Pageable pageable);

    /** Sequence source for CpfLoanApplicationService's "CPFL/{finYear}/{seq}" voucher numbering. */
    long countByLoanApplicationNoStartingWith(String prefix);

    /** Loan(s) still mid-lifecycle for this employee - APPLIED/SANCTIONED/DISBURSED and not yet fully recovered (recoveryPhase not CLOSED). */
    @Query("SELECT l FROM CpfLoanApplication l WHERE l.employee.id = :employeeId "
            + "AND l.status IN ('APPLIED', 'SANCTIONED', 'DISBURSED') AND l.recoveryPhase <> 'CLOSED'")
    List<CpfLoanApplication> findActiveLoanByEmployeeId(Long employeeId);
}
