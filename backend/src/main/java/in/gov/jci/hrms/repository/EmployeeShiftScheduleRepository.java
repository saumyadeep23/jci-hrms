package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface EmployeeShiftScheduleRepository extends JpaRepository<EmployeeShiftSchedule, Long> {

    Optional<EmployeeShiftSchedule> findByEmployeeIdAndScheduleDate(Long employeeId, LocalDate scheduleDate);
}
