package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeEmploymentCategoryRepository extends JpaRepository<EmployeeEmploymentCategory, Long> {

    Optional<EmployeeEmploymentCategory> findByEmployeeId(Long employeeId);

    /** Batched lookup for a page of employees (e.g. EmployeeService.list) - avoids an N+1 query per row. */
    List<EmployeeEmploymentCategory> findByEmployeeIdIn(Collection<Long> employeeIds);
}
