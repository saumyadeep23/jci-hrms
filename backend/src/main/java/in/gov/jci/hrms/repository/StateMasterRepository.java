package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StateMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateMasterRepository extends JpaRepository<StateMaster, UUID> {

    Optional<StateMaster> findByStateCode(String stateCode);

    Optional<StateMaster> findByStateNameIgnoreCase(String stateName);

    List<StateMaster> findByActiveTrueOrderByStateNameAsc();
}
