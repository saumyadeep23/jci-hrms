package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveEncashmentApplicationRepository extends JpaRepository<LeaveEncashmentApplication, Long> {

    List<LeaveEncashmentApplication> findByEmployeeId(Long employeeId);
}
