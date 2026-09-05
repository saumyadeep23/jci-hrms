package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.OutsourcedSalaryBreakdownItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutsourcedSalaryBreakdownItemRepository extends JpaRepository<OutsourcedSalaryBreakdownItem, Long> {

    List<OutsourcedSalaryBreakdownItem> findByEmployeeId(Long employeeId);
}
