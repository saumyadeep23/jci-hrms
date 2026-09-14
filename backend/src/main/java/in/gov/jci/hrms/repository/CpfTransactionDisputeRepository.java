package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfTransactionDispute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CpfTransactionDisputeRepository extends JpaRepository<CpfTransactionDispute, Long> {

    List<CpfTransactionDispute> findByEmployee_IdOrderByRaisedAtDesc(Long employeeId);

    Optional<CpfTransactionDispute> findByIdAndEmployee_Id(Long id, Long employeeId);

    Page<CpfTransactionDispute> findByStatus(CpfDisputeStatus status, Pageable pageable);

    Page<CpfTransactionDispute> findByAssignedTo_Id(Long assignedToEmployeeId, Pageable pageable);

    long countByStatus(CpfDisputeStatus status);

    /** Duplicate-active-dispute check (Part 10) - mirrors V83's own partial unique index (ux_cpf_dispute_active_per_txn_category) in application code, so CpfTransactionDisputeService can surface a clean 409 instead of a raw constraint-violation 500. */
    boolean existsByCpfLedgerTransaction_IdAndDisputeCategoryAndStatusNotIn(
            Long cpfLedgerTransactionId, CpfDisputeCategory disputeCategory, List<CpfDisputeStatus> excludedStatuses);

    /** GET /admin/cpf/disputes' dispute-status-per-transaction batch lookup - one query for a whole page of passbook transactions, never one dispute query per row (Part 25). Only the latest/active dispute matters for the passbook's own "hasActiveDispute" badge. */
    List<CpfTransactionDispute> findByCpfLedgerTransaction_IdInOrderByRaisedAtDesc(List<Long> ledgerTransactionIds);

    long countByDisputeNumberStartingWith(String prefix);

    @Query("SELECT d FROM CpfTransactionDispute d WHERE "
            + "(:disputeNumber IS NULL OR d.disputeNumber = :disputeNumber) AND "
            + "(:employeeId IS NULL OR d.employee.id = :employeeId) AND "
            + "(:category IS NULL OR d.disputeCategory = :category) AND "
            + "(:status IS NULL OR d.status = :status)")
    Page<CpfTransactionDispute> search(@org.springframework.data.repository.query.Param("disputeNumber") String disputeNumber,
                                        @org.springframework.data.repository.query.Param("employeeId") Long employeeId,
                                        @org.springframework.data.repository.query.Param("category") CpfDisputeCategory category,
                                        @org.springframework.data.repository.query.Param("status") CpfDisputeStatus status,
                                        Pageable pageable);
}
