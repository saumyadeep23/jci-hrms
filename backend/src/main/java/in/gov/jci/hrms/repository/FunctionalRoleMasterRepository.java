package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.FunctionalRoleMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FunctionalRoleMasterRepository extends JpaRepository<FunctionalRoleMaster, UUID> {

    Optional<FunctionalRoleMaster> findByRoleCode(String roleCode);

    List<FunctionalRoleMaster> findAllByOrderByRoleCodeAsc();
}
