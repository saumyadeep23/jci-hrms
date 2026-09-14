package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.CpfHeadMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CpfHeadMasterRepository extends JpaRepository<CpfHeadMaster, UUID> {

    List<CpfHeadMaster> findAllByOrderByCodeAsc();

    Optional<CpfHeadMaster> findByCode(String code);
}
