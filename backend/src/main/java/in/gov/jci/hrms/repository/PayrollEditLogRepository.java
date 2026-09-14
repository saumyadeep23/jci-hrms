package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollEditLogRepository extends JpaRepository<PayrollEditLog, Long> {

    List<PayrollEditLog> findByRecord_TranIdOrderByEditedAtDesc(Long tranId);
}
