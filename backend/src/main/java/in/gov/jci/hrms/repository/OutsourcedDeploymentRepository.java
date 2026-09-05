package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.OutsourcedDeployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OutsourcedDeploymentRepository extends JpaRepository<OutsourcedDeployment, Long> {

    Optional<OutsourcedDeployment> findByEmployeeIdAndCurrentTrue(Long employeeId);
}
