package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.JoiningStatus;
import in.gov.jci.hrms.entity.MovementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeMovementRecordRepository extends JpaRepository<EmployeeMovementRecord, Long> {

    boolean existsByJoiningReportNo(String joiningReportNo);

    Page<EmployeeMovementRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Tab 2 - Pending Releases. */
    List<EmployeeMovementRecord> findByMovementStatusOrderByCreatedAtAsc(MovementStatus movementStatus);

    /** Tab 3 - Joining Verifications. */
    List<EmployeeMovementRecord> findByJoiningStatusOrderByJoiningDbTimestampAsc(JoiningStatus joiningStatus);

    List<EmployeeMovementRecord> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

    /** Order-level PDF (Transfer/Promotion Order) table rows - an order can cover several employees. */
    List<EmployeeMovementRecord> findByOrderIdOrderById(Long orderId);
}
