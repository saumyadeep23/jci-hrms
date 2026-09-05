package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.SalaryHeadMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SalaryHeadMasterRepository extends JpaRepository<SalaryHeadMaster, Long> {

    Optional<SalaryHeadMaster> findByCode(String code);
}
