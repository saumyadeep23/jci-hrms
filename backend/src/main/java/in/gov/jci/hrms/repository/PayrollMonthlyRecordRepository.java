package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PayrollMonthlyRecordRepository extends JpaRepository<PayrollMonthlyRecord, Long> {

    Page<PayrollMonthlyRecord> findByBatch_Id(Long batchId, Pageable pageable);

    List<PayrollMonthlyRecord> findByBatch_Id(Long batchId);

    /** "Exact historical figures" lookup for one employee's one already-run PayrollBatch - IdaProjectionEngineService/IdaArrearComputationService's own source for a finalized retro month, see PayrollBatch's uq_payroll_record_batch_emp unique constraint. */
    Optional<PayrollMonthlyRecord> findByBatch_IdAndEmployee_Id(Long batchId, Long employeeId);

    /** Cascades to payroll_monthly_head_items via the DB's own ON DELETE CASCADE - re-running processBatch() must start from a clean slate. */
    @Transactional
    long deleteByBatch_Id(Long batchId);
}
