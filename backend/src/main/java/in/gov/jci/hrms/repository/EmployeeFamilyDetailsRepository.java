package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeFamilyDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeFamilyDetailsRepository extends JpaRepository<EmployeeFamilyDetails, Long> {

    Optional<EmployeeFamilyDetails> findByEmployeeId(Long employeeId);
}
