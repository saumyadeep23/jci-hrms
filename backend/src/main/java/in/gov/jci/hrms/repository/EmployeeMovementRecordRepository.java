package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.JoiningStatus;
import in.gov.jci.hrms.entity.MovementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * PostIncumbencyService's auto-close guard: before a new SUBSTANTIVE incumbent's creation is
     * allowed to close out a post's prior SUBSTANTIVE incumbent, confirm that outgoing employee
     * actually has a movement recorded FROM that exact post (department/designation/office) whose
     * movementStatus has reached RELIEVED or JOINED. Without this, an unrelated or mistaken
     * post-assignment would silently evict a still-serving employee's incumbency record with no
     * trace of them ever having left. officeId may be null (DPC-based posts carry no ro_id), in
     * which case only a movement record whose own fromOffice is also null can match.
     */
    @Query("SELECT COUNT(m) > 0 FROM EmployeeMovementRecord m WHERE m.employee.id = :employeeId "
            + "AND m.fromDepartment.id = :departmentId AND m.fromDesignation.id = :designationId "
            + "AND ((:officeId IS NULL AND m.fromOffice IS NULL) OR m.fromOffice.id = :officeId) "
            + "AND m.movementStatus IN :statuses")
    boolean existsReleasedMovementFromPost(@Param("employeeId") Long employeeId,
                                            @Param("departmentId") Long departmentId,
                                            @Param("designationId") Long designationId,
                                            @Param("officeId") Long officeId,
                                            @Param("statuses") Collection<MovementStatus> statuses);
}
