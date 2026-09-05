package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /** Now potentially more than one row (one per state, plus at most one national row) - see V39. */
    List<Holiday> findByHolidayDate(LocalDate holidayDate);

    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}
