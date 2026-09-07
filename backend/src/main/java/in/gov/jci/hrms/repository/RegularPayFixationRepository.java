package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.RegularPayFixation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegularPayFixationRepository extends JpaRepository<RegularPayFixation, Long> {

    Optional<RegularPayFixation> findByEmployeeIdAndCurrentTrue(Long employeeId);

    /** Full fixation history for one employee, oldest first - PayrollBatchComputationService intersects each row's [effectiveFrom, effectiveTo] against the batch period to split a mid-month promotion's Earned Basic across the old/new rate. */
    List<RegularPayFixation> findByEmployee_IdOrderByEffectiveFromAsc(Long employeeId);

    /** Batched form of findByEmployeeIdAndCurrentTrue - one query for a whole list's worth of employees, so a listing (e.g. the encashment admin review queue) doesn't resolve current basic pay per row. */
    List<RegularPayFixation> findByEmployeeIdInAndCurrentTrue(List<Long> employeeIds);

    /**
     * TerminalSettlementService's "latest applicable fixation" lookup - the
     * still-current row if EmployeeReleaseService hasn't run yet, or the
     * one it just closed out with effectiveTo = separationDate if it has
     * (settlement can be generated either before or after the release
     * workflow closes the fixation, so both must resolve to the same row).
     * Ordered most-recent-first so callers can just take the first result.
     */
    @Query("SELECT r FROM RegularPayFixation r WHERE r.employee.id = :employeeId "
            + "AND (r.current = true OR r.effectiveTo = :separationDate) ORDER BY r.effectiveFrom DESC")
    List<RegularPayFixation> findApplicableForSettlement(@Param("employeeId") Long employeeId, @Param("separationDate") LocalDate separationDate);

    /**
     * Closes the employee's current fixation via a direct bulk UPDATE rather than loading the entity
     * and mutating it - MovementOrderService.applyPromotionPayFixation() used to do the latter, and hit
     * two real bugs doing so: Hibernate's single-flush statement ordering runs INSERTs before UPDATEs
     * regardless of code order, so the new is_current=true row was being inserted while this one was
     * still is_current=true in the DB (violating idx_uq_current_regular_fixation); and re-saving the
     * already-managed entity to force an earlier flush instead nulled out its lazy, never-dereferenced
     * gradeScale/scale_code association on merge. A bulk HQL UPDATE sidesteps both - it never loads the
     * row as an entity at all, so there is no lazy association to lose and no ordering to fight.
     * clearAutomatically detaches whatever's already in the persistence context so a later read in the
     * same transaction doesn't see a stale is_current=true cached copy of this row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RegularPayFixation f SET f.current = false, f.effectiveTo = :effectiveTo, f.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE f.employee.id = :employeeId AND f.current = true")
    int closeCurrentFixation(@Param("employeeId") Long employeeId, @Param("effectiveTo") LocalDate effectiveTo);
}
