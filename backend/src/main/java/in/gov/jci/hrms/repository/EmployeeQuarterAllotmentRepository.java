package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.QuarterAllotmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeQuarterAllotmentRepository extends JpaRepository<EmployeeQuarterAllotment, Long> {

    List<EmployeeQuarterAllotment> findByEmployee_IdOrderByAllottedFromDesc(Long employeeId);

    /** Returns 0 or more - the service treats any non-empty result as "already occupied" (uniqueness is enforced there, not by a DB constraint). */
    List<EmployeeQuarterAllotment> findByEmployee_IdAndStatus(Long employeeId, QuarterAllotmentStatus status);
}
