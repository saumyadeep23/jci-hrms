package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AparCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AparCycleRepository extends JpaRepository<AparCycle, Long> {

    Optional<AparCycle> findByCycleYear(String cycleYear);
}
