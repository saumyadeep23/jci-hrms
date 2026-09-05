package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DistrictMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DistrictMasterRepository extends JpaRepository<DistrictMaster, UUID> {

    Page<DistrictMaster> findByStateId(UUID stateId, Pageable pageable);

    boolean existsByStateId(UUID stateId);
}
