package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunScope;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CpfAnnualInterestRunRepository extends JpaRepository<CpfAnnualInterestRun, Long> {

    List<CpfAnnualInterestRun> findByFinYear(String finYear);

    /** The one active (non-REVERSED/FAILED) ALL_MEMBERS run for a FY, if any - matches V81's partial unique index. */
    Optional<CpfAnnualInterestRun> findByFinYearAndScopeAndStatusNotIn(String finYear, CpfInterestRunScope scope, List<CpfInterestRunStatus> excludedStatuses);

    /** The one active (non-REVERSED/FAILED) SELECTED_MEMBER run for a FY+member, if any - matches V81's partial unique index. */
    Optional<CpfAnnualInterestRun> findByFinYearAndScopeAndMemberEmployee_IdAndStatusNotIn(
            String finYear, CpfInterestRunScope scope, Long memberEmployeeId, List<CpfInterestRunStatus> excludedStatuses);

    List<CpfAnnualInterestRun> findAllByOrderByFinYearDescCalculatedAtDesc();

    List<CpfAnnualInterestRun> findByMemberEmployee_IdOrderByFinYearDesc(Long memberEmployeeId);
}
