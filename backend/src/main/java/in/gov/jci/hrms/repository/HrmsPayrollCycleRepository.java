package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HrmsPayrollCycleRepository extends JpaRepository<HrmsPayrollCycle, Long> {

    Optional<HrmsPayrollCycle> findByCycleCode(String cycleCode);
}
