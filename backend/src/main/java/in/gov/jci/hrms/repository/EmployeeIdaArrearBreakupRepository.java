package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeIdaArrearBreakup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeIdaArrearBreakupRepository extends JpaRepository<EmployeeIdaArrearBreakup, Long> {

    /** uq_emp_retro_history - lets materializeRealizedArrears() be re-run idempotently for an employee/order/month already realized. */
    Optional<EmployeeIdaArrearBreakup> findByEmployee_IdAndDaRateHistory_IdAndRetroMonthAndRetroYear(
            Long employeeId, Long daRateHistoryId, int retroMonth, int retroYear);

    List<EmployeeIdaArrearBreakup> findByEmployee_IdAndDaRateHistory_Id(Long employeeId, Long daRateHistoryId);
}
