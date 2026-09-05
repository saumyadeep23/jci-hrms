package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeQualification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeQualificationRepository extends JpaRepository<EmployeeQualification, Long> {

    List<EmployeeQualification> findByEmployeeIdOrderByPassingYearDesc(Long employeeId);
}
