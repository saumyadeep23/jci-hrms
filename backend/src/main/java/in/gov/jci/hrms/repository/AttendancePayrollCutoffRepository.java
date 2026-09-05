package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AttendancePayrollCutoff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendancePayrollCutoffRepository extends JpaRepository<AttendancePayrollCutoff, Long> {

    /** Both args are the same date - checks periodStart &lt;= date &lt;= periodEnd for a frozen cutoff. */
    Optional<AttendancePayrollCutoff> findFirstByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqualAndFrozenTrue(
            LocalDate date, LocalDate sameDate);
}
