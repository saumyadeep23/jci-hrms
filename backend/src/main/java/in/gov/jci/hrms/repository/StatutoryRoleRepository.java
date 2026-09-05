package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.StatutoryRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatutoryRoleRepository extends JpaRepository<StatutoryRole, Long> {

    List<StatutoryRole> findByEmployeeId(Long employeeId);
}
