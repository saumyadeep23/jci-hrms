package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.RegisteredDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegisteredDeviceRepository extends JpaRepository<RegisteredDevice, Long> {

    List<RegisteredDevice> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    Optional<RegisteredDevice> findByEmployeeIdAndDeviceIdentifier(Long employeeId, String deviceIdentifier);

    List<RegisteredDevice> findAllByOrderByCreatedAtDesc();
}
