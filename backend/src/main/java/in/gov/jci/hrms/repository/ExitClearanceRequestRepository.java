package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExitClearanceRequestRepository extends JpaRepository<ExitClearanceRequest, Long> {

    List<ExitClearanceRequest> findByEmployeeId(Long employeeId);

    /** Most recent non-cancelled request for an employee, if any - used to check "does this employee already have an open exit request" before auto-creating one. */
    Optional<ExitClearanceRequest> findFirstByEmployeeIdAndStatusNotOrderByInitiatedDateDesc(Long employeeId, ExitClearanceStatus excludedStatus);

    boolean existsByEmployeeIdAndStatusNot(Long employeeId, ExitClearanceStatus excludedStatus);

    List<ExitClearanceRequest> findByStatus(ExitClearanceStatus status);

    /** ExitInitiationScheduler's 90-day lookahead window - superannuation_date itself lives on employee_superannuation_details, joined by the caller; this only needs the employee id list already resolved. */
    List<ExitClearanceRequest> findByEmployeeIdIn(List<Long> employeeIds);
}
