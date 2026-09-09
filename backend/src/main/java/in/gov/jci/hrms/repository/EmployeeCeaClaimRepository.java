package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.EmployeeCeaClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeeCeaClaimRepository extends JpaRepository<EmployeeCeaClaim, Long> {

    boolean existsByClaimNo(String claimNo);

    List<EmployeeCeaClaim> findByEmployeeId(Long employeeId);

    /** PayrollBatchComputationService.processBatch()'s per-employee pickup - matches idx_cea_claims_payroll_pickup exactly. */
    @Query("SELECT c FROM EmployeeCeaClaim c WHERE c.employee.id = :employeeId AND c.claimStatus = 'BILL_PASSED' AND c.payrollProcessed = false")
    List<EmployeeCeaClaim> findPendingPayrollDisbursement(@Param("employeeId") Long employeeId);

    /** CeaClaimService's 2-child-per-academic-year rule - excludes REJECTED claims (a rejected claim never occupied one of the two slots). */
    List<EmployeeCeaClaim> findByEmployeeIdAndAcademicYearAndClaimStatusNot(Long employeeId, String academicYear, CeaClaimStatus status);

    /** CeaClaimController's HR "pending verification" (SUBMITTED) and Finance "pending bill passing" (VERIFIED) queues - mirrors EmployeeMovementRecordRepository.findByMovementStatusOrderByCreatedAtAsc's own "Pending X" pattern. */
    List<EmployeeCeaClaim> findByClaimStatusOrderByCreatedAtAsc(CeaClaimStatus claimStatus);

    /** CeaClaimController's officer-facing "History / Log" view - every claim regardless of status, most recent first. */
    List<EmployeeCeaClaim> findAllByOrderByCreatedAtDesc();
}
