package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AttendanceDetailStatus;
import in.gov.jci.hrms.entity.DailyAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyAttendanceRepository extends JpaRepository<DailyAttendance, Long> {

    List<DailyAttendance> findByEmployeeId(Long employeeId);

    Optional<DailyAttendance> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    List<DailyAttendance> findByEmployeeIdAndAttendanceDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    /**
     * Month-to-date concession-occurrence count (D4) - Spring Data's Between
     * is inclusive on both ends, so callers pass date.minusDays(1) as the
     * upper bound to count strictly-prior days. Lets a single-day re-run
     * count correctly without replaying the whole month.
     */
    long countByEmployeeIdAndAttendanceDateBetweenAndDetailStatusIn(
            Long employeeId, LocalDate monthStartInclusive, LocalDate priorToDateInclusive, Collection<AttendanceDetailStatus> statuses);
}
