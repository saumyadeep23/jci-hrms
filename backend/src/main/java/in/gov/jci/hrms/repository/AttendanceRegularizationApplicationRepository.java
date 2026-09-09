package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceRegularizationApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRegularizationApplicationRepository extends JpaRepository<AttendanceRegularizationApplication, Long> {

    List<AttendanceRegularizationApplication> findByEmployeeId(Long employeeId);

    /** Duplicate-submission guard (AttendanceRegularizationService.submit()) - at most one PENDING request per employee/date, backed by uq_pending_attendance_regularization (V73). */
    boolean existsByEmployeeIdAndAttendanceDateAndApprovalStatus(Long employeeId, LocalDate attendanceDate, ApprovalStatus approvalStatus);

    /** HoD's own full decision history (all statuses) - RegularizationApprovalQueuePage's metric cards and status filter derive Pending/Approved/Rejected-this-month counts from this client-side, rather than a separate paginated/date-ranged endpoint. */
    List<AttendanceRegularizationApplication> findByDesignatedApproverId(Long designatedApproverId);

    Optional<AttendanceRegularizationApplication> findByDailyAttendanceIdAndApprovalStatus(
            Long dailyAttendanceId, ApprovalStatus approvalStatus);

    /** HoD Regularization Approval Queue (ALMS Phase 3). */
    List<AttendanceRegularizationApplication> findByDesignatedApproverIdAndApprovalStatus(
            Long designatedApproverId, ApprovalStatus approvalStatus);
}
