package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfAnnualInterestRun;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CpfAnnualInterestRunRepository extends JpaRepository<CpfAnnualInterestRun, Long> {

    List<CpfAnnualInterestRun> findByFinYear(String finYear);

    List<CpfAnnualInterestRun> findByFinYearAndStatus(String finYear, CpfInterestRunStatus status);
}
