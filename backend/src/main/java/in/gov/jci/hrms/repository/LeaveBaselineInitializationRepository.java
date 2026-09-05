package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveBaselineInitialization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBaselineInitializationRepository extends JpaRepository<LeaveBaselineInitialization, UUID> {

    Optional<LeaveBaselineInitialization> findByEmployeeIdAndLeaveTypeIdAndAsOnDate(
            Long employeeId, Long leaveTypeId, LocalDate asOnDate);

    boolean existsByEmployeeIdAndLeaveTypeId(Long employeeId, Long leaveTypeId);
}
