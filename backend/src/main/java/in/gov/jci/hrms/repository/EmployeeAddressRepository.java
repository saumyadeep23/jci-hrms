package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.AddressType;
import in.gov.jci.hrms.entity.EmployeeAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeAddressRepository extends JpaRepository<EmployeeAddress, Long> {

    List<EmployeeAddress> findByEmployeeId(Long employeeId);

    Optional<EmployeeAddress> findByEmployeeIdAndAddressType(Long employeeId, AddressType addressType);
}
