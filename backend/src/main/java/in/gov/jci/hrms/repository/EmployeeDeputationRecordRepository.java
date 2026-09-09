package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DeputationDirection;
import in.gov.jci.hrms.entity.DeputationStatus;
import in.gov.jci.hrms.entity.EmployeeDeputationRecord;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeDeputationRecordRepository extends JpaRepository<EmployeeDeputationRecord, Long> {

    Optional<EmployeeDeputationRecord> findByEmployee_IdAndStatus(Long employeeId, DeputationStatus status);

    /** Every deputation whose status is still ACTIVE and whose period actually covers asOfDate - a record can be status=ACTIVE past its own periodTo if repatriation processing is overdue, which this excludes. */
    @Query("SELECT d FROM EmployeeDeputationRecord d WHERE d.status = in.gov.jci.hrms.entity.DeputationStatus.ACTIVE "
            + "AND d.periodFrom <= :asOfDate AND d.periodTo >= :asOfDate")
    List<EmployeeDeputationRecord> findAllActiveDeputations(@Param("asOfDate") LocalDate asOfDate);

    /** Spec's own literal signature took raw Strings - corrected to the real DeputationDirection/DeputationStatus enums, matching every other status-filtered finder in this codebase. */
    List<EmployeeDeputationRecord> findByDeputationDirectionAndStatus(DeputationDirection direction, DeputationStatus status);
}
