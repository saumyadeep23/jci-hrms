package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.MobilePunch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MobilePunchRepository extends JpaRepository<MobilePunch, Long> {

    List<MobilePunch> findByEmployeeId(Long employeeId);

    List<MobilePunch> findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(Long employeeId, Instant start, Instant end);
}
