package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LeaveEncashmentApplicationRepository extends JpaRepository<LeaveEncashmentApplication, Long> {

    List<LeaveEncashmentApplication> findByEmployeeId(Long employeeId);

    /** Eagerly loads employee + designation in one query - the admin review (Gate 1/Gate 2) queues render both for every row, and would otherwise lazy-load each per row. */
    @Query("SELECT a FROM LeaveEncashmentApplication a JOIN FETCH a.employee e JOIN FETCH e.designation ORDER BY a.createdAt DESC")
    List<LeaveEncashmentApplication> findAllWithEmployeeAndDesignation();

    /** Finance-approved, not yet queued into any payroll run - PayrollRunService.compute() pulls all of these once per run rather than per employee. */
    List<LeaveEncashmentApplication> findByFinanceApprovalStatusAndPayrollRunIsNull(ApprovalStatus financeApprovalStatus);

    /** An arrear computed (checkForRetroactiveArrear()) but not yet queued into any payroll run for its own, separate disbursement. */
    List<LeaveEncashmentApplication> findByArrearSettledFalseAndArrearPayrollRunIsNull();

    List<LeaveEncashmentApplication> findByPayrollRunId(Long payrollRunId);

    List<LeaveEncashmentApplication> findByArrearPayrollRunId(Long payrollRunId);

    /**
     * Head 20 candidates for PayrollBatchComputationService.processBatch() - Finance-approved,
     * payroll-eligible, not yet locked by a finalized batch, and either unclaimed by any batch or
     * already claimed by THIS one (so a re-run of the same still-DRAFT batch picks its own
     * previously-tagged rows back up rather than treating them as newly eligible only via the "still
     * unclaimed" branch). The 25th-of-month finance-approval cutoff is NOT applied here - it's filtered
     * in PayrollBatchComputationService itself so that rule stays covered by plain Mockito unit tests
     * rather than needing a real database to exercise a JPQL date comparison.
     */
    @Query("SELECT a FROM LeaveEncashmentApplication a WHERE a.financeApprovalStatus = :status "
            + "AND a.payrollEligible = true AND a.payrollProcessed = false "
            + "AND (a.payrollBatch IS NULL OR a.payrollBatch.id = :batchId)")
    List<LeaveEncashmentApplication> findEligibleForPayrollBatch(@Param("status") ApprovalStatus status,
                                                                   @Param("batchId") Long batchId);

    /** Rows this batch previously tagged (a prior processBatch() run) but hasn't locked yet - released back to unclaimed before re-tagging. */
    List<LeaveEncashmentApplication> findByPayrollBatch_IdAndPayrollProcessedFalse(Long batchId);

    /** Rows tagged to this batch, for finalizeBatch() to lock (is_payroll_processed = true). */
    List<LeaveEncashmentApplication> findByPayrollBatch_Id(Long batchId);

    /**
     * IdaArrearComputationService.materializeRealizedArrears()'s "encashments settled in the retro
     * window" lookup - dual-approved REGULAR applications (a DA_ARREAR child is never itself eligible
     * to spawn another arrear) whose finance sanction fell inside [from, to].
     */
    @Query("SELECT a FROM LeaveEncashmentApplication a WHERE a.employee.id = :employeeId "
            + "AND a.applicationType = in.gov.jci.hrms.entity.EncashmentApplicationType.REGULAR "
            + "AND a.hrApprovalStatus = in.gov.jci.hrms.entity.ApprovalStatus.APPROVED "
            + "AND a.financeApprovalStatus = in.gov.jci.hrms.entity.ApprovalStatus.APPROVED "
            + "AND a.financeApprovedAt BETWEEN :from AND :to")
    List<LeaveEncashmentApplication> findSettledInWindow(@Param("employeeId") Long employeeId,
                                                          @Param("from") Instant from, @Param("to") Instant to);
}
