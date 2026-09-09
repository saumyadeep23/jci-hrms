package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.VehicleAllotmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeVehicleAllotmentRepository extends JpaRepository<EmployeeVehicleAllotment, Long> {

    List<EmployeeVehicleAllotment> findByEmployee_IdOrderByAllottedFromDesc(Long employeeId);

    List<EmployeeVehicleAllotment> findByEmployee_IdAndStatus(Long employeeId, VehicleAllotmentStatus status);
}
