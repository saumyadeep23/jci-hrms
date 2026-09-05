package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeePastServiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeePastServiceRecordRepository extends JpaRepository<EmployeePastServiceRecord, Long> {

    List<EmployeePastServiceRecord> findByEmployeeIdOrderByFromDateDesc(Long employeeId);
}
