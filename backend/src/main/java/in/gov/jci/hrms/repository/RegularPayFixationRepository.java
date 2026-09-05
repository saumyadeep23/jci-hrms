package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.RegularPayFixation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegularPayFixationRepository extends JpaRepository<RegularPayFixation, Long> {

    Optional<RegularPayFixation> findByEmployeeIdAndCurrentTrue(Long employeeId);

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
}
