package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.UnifiedSalaryHeadHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnifiedSalaryHeadHistoryRepository extends JpaRepository<UnifiedSalaryHeadHistory, Long> {

    List<UnifiedSalaryHeadHistory> findByEmployeeIdOrderByCycleYearAscCycleMonthAscSalaryHeadCodeAsc(Long employeeId);
}
