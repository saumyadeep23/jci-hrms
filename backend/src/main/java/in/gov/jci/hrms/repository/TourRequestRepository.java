package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.entity.TourRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TourRequestRepository extends JpaRepository<TourRequest, Long> {

    Optional<TourRequest> findByRequestNumber(String requestNumber);

    List<TourRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, TourRequestStatus status, LocalDate startDate, LocalDate endDate);
}
