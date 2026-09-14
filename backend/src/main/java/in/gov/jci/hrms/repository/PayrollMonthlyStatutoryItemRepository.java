package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayrollMonthlyStatutoryItemRepository extends JpaRepository<PayrollMonthlyStatutoryItem, Long> {

    List<PayrollMonthlyStatutoryItem> findByRecord_TranId(Long tranId);

    /** Batch lookup across many tranIds at once - see PayrollMonthlyRecordRepository.findByEmployee_IdAndYearIn's own javadoc for why. */
    List<PayrollMonthlyStatutoryItem> findByRecord_TranIdIn(List<Long> tranIds);
}
