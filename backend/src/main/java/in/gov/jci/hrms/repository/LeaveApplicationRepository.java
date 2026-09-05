package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.LeaveApplication;
import in.gov.jci.hrms.entity.LeaveApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {

    List<LeaveApplication> findByEmployeeId(Long employeeId);

    /** Any sanctioned application whose [startDate, endDate] span covers the given date. */
    List<LeaveApplication> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveApplicationStatus status, LocalDate date1, LocalDate date2);

    /** Sanctioned applications ending on this date - used to detect a new application starting the very next day (contiguous-CL rule). */
    List<LeaveApplication> findByEmployeeIdAndStatusAndEndDate(Long employeeId, LeaveApplicationStatus status, LocalDate endDate);

    /** Sanctioned applications starting on this date - used to detect a new application ending the day before (contiguous-CL rule). */
    List<LeaveApplication> findByEmployeeIdAndStatusAndStartDate(Long employeeId, LeaveApplicationStatus status, LocalDate startDate);
}
