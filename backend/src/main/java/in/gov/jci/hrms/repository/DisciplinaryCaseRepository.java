package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DisciplinaryCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisciplinaryCaseRepository extends JpaRepository<DisciplinaryCase, Long> {

    Optional<DisciplinaryCase> findByCaseNumber(String caseNumber);

    List<DisciplinaryCase> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
}
