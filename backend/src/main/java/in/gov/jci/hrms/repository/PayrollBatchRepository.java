package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, Long> {

    Optional<PayrollBatch> findBySalMonthAndSalYear(int salMonth, int salYear);
}
