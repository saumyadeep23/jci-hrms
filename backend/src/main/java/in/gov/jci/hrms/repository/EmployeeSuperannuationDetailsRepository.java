package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeSuperannuationDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeSuperannuationDetailsRepository extends JpaRepository<EmployeeSuperannuationDetails, Long> {

    Optional<EmployeeSuperannuationDetails> findByEmployeeId(Long employeeId);

    /** ExitInitiationScheduler's 90-day lookahead window - filtered further (ACTIVE status, no open exit request) by the caller. */
    List<EmployeeSuperannuationDetails> findBySuperannuationDateBetween(LocalDate from, LocalDate to);

    /** SuperannuationScheduledTask's daily due-for-retirement sweep. */
    List<EmployeeSuperannuationDetails> findBySuperannuationDateLessThan(LocalDate date);
}
