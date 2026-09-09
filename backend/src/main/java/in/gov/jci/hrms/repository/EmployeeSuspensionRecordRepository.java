package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.SuspensionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeSuspensionRecordRepository extends JpaRepository<EmployeeSuspensionRecord, Long> {

    Optional<EmployeeSuspensionRecord> findByEmployee_IdAndStatus(Long employeeId, SuspensionStatus status);

    /**
     * Every active suspension - callers (PayrollBatchComputationService, the Suspended Staff report)
     * separately look up EmployeeSuspensionNecRepository.findBySuspension_IdAndSalMonthAndSalYear() per
     * record for the (month, year) they actually care about. (month, year) aren't used to filter this
     * query itself - a FETCH JOIN with an ON/WITH condition on the fetched collection is rejected outright
     * by Hibernate ("Fetch join has a 'with' clause (use a filter instead)"), which is what an earlier
     * version of this method tried and which broke every Spring context in this app at startup, since
     * Spring Data validates every @Query at repository-proxy creation time.
     */
    @Query("SELECT s FROM EmployeeSuspensionRecord s WHERE s.status = in.gov.jci.hrms.entity.SuspensionStatus.UNDER_SUSPENSION")
    List<EmployeeSuspensionRecord> findActiveSuspensionsWithNecStatus(@Param("month") int month, @Param("year") int year);

    /** Spec's own literal signature took a raw String - corrected to the real SuspensionStatus enum, matching every other status-filtered finder in this codebase. */
    List<EmployeeSuspensionRecord> findByStatus(SuspensionStatus status);
}
