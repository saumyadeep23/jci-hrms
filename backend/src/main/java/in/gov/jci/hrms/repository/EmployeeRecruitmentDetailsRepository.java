package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeRecruitmentDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRecruitmentDetailsRepository extends JpaRepository<EmployeeRecruitmentDetails, Long> {

    Optional<EmployeeRecruitmentDetails> findByEmployeeId(Long employeeId);
}
