package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.AttendanceRegularizationApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceRegularizationApplicationRepository extends JpaRepository<AttendanceRegularizationApplication, Long> {

    List<AttendanceRegularizationApplication> findByEmployeeId(Long employeeId);

    Optional<AttendanceRegularizationApplication> findByDailyAttendanceIdAndApprovalStatus(
            Long dailyAttendanceId, ApprovalStatus approvalStatus);

    /** HoD Regularization Approval Queue (ALMS Phase 3). */
    List<AttendanceRegularizationApplication> findByDesignatedApproverIdAndApprovalStatus(
            Long designatedApproverId, ApprovalStatus approvalStatus);
}
