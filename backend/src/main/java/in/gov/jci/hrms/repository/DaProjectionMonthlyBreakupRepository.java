package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.DaProjectionMonthlyBreakup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DaProjectionMonthlyBreakupRepository extends JpaRepository<DaProjectionMonthlyBreakup, Long> {

    List<DaProjectionMonthlyBreakup> findByBatch_IdOrderByEmployee_IdAscSalYearAscSalMonthAsc(Long batchId);

    /** EmployeeArrearScheduleTable's per-employee month-by-month accordion drill-down. */
    List<DaProjectionMonthlyBreakup> findByProjectionEmployee_IdOrderBySalYearAscSalMonthAsc(Long projectionEmployeeId);
}
