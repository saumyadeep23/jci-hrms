package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeServiceBook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeServiceBookRepository extends JpaRepository<EmployeeServiceBook, Long> {

    List<EmployeeServiceBook> findByEmployeeIdOrderByEventDateAscIdAsc(Long employeeId);
}
