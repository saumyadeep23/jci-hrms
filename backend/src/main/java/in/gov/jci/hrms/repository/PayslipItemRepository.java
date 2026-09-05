package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayslipItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayslipItemRepository extends JpaRepository<PayslipItem, Long> {

    List<PayslipItem> findByPayslipId(Long payslipId);
}
