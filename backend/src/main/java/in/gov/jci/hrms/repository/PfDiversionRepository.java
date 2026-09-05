package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PfDiversion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PfDiversionRepository extends JpaRepository<PfDiversion, Long> {

    List<PfDiversion> findByEmployeeId(Long employeeId);
}
