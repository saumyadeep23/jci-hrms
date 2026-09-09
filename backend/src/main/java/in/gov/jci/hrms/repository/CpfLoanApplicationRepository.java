package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CpfLoanApplicationRepository extends JpaRepository<CpfLoanApplication, Long> {

    List<CpfLoanApplication> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

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
