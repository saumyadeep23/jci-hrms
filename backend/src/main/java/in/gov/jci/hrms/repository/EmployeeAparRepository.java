package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeApar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeAparRepository extends JpaRepository<EmployeeApar, Long> {

    List<EmployeeApar> findByEmployeeId(Long employeeId);

    List<EmployeeApar> findByAparCycleId(Long aparCycleId);
}
