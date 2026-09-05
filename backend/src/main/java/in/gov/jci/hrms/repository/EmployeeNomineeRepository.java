package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeNominee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeNomineeRepository extends JpaRepository<EmployeeNominee, Long> {

    List<EmployeeNominee> findByEmployeeId(Long employeeId);
}
