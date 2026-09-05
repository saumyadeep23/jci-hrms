package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeSocialProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeSocialProfileRepository extends JpaRepository<EmployeeSocialProfile, Long> {

    Optional<EmployeeSocialProfile> findByEmployeeId(Long employeeId);
}
