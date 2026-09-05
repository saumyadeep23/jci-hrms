package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {

    Optional<PayrollRun> findByCycleYearAndCycleMonth(Integer cycleYear, Integer cycleMonth);
}
