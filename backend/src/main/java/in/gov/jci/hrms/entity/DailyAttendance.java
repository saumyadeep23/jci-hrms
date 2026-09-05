package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row per employee per calendar day (unique on employee_id +
 * attendance_date, enforced in the V7 migration). No deleted_at column -
 * unlike most entities in this schema, a day's attendance record isn't
 * soft-deletable, only correctable via update.
 */
@Entity
@Table(name = "daily_attendance")
@EntityListeners(AuditableEntityListener.class)
public class DailyAttendance implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "in_time")
    private Instant inTime;

    @Column(name = "out_time")
    private Instant outTime;

    @Column(name = "total_working_hours", precision = 4, scale = 2)
    private BigDecimal totalWorkingHours;

    /** Fine-grained pipeline status (AttendanceAggregationService) - status above stays the coarse, payroll-facing value; see toCoarseStatus(). */
    @Enumerated(EnumType.STRING)
    @Column(name = "detail_status", length = 30)
    private AttendanceDetailStatus detailStatus;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_application_id")
    private LeaveApplication leaveApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_request_id")
    private TourRequest tourRequest;

    /** Set true only by AttendanceRegularizationService.approve() - see its javadoc for the recompute/refund it performs alongside this flag. */
    @Column(name = "is_regularized", nullable = false)
    private boolean regularized = false;

    /** True while a 0.5-day AttendanceLeaveDeductionService penalty debit for this day stands un-refunded - set false again once a regularization approval refunds it. */
    @Column(name = "auto_penalty_debited", nullable = false)
    private boolean autoPenaltyDebited = false;

    @Column(name = "total_work_minutes")
    private Integer totalWorkMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DailyAttendance() {
    }

    public DailyAttendance(Employee employee, LocalDate attendanceDate, AttendanceStatus status) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public Instant getInTime() {
        return inTime;
    }

    public void setInTime(Instant inTime) {
        this.inTime = inTime;
    }

    public Instant getOutTime() {
        return outTime;
    }

    public void setOutTime(Instant outTime) {
        this.outTime = outTime;
    }

    public BigDecimal getTotalWorkingHours() {
        return totalWorkingHours;
    }

    public void setTotalWorkingHours(BigDecimal totalWorkingHours) {
        this.totalWorkingHours = totalWorkingHours;
    }

    public AttendanceDetailStatus getDetailStatus() {
        return detailStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public LeaveApplication getLeaveApplication() {
        return leaveApplication;
    }

    public TourRequest getTourRequest() {
        return tourRequest;
    }

    public boolean isRegularized() {
        return regularized;
    }

    public void setRegularized(boolean regularized) {
        this.regularized = regularized;
    }

    public boolean isAutoPenaltyDebited() {
        return autoPenaltyDebited;
    }

    public void setAutoPenaltyDebited(boolean autoPenaltyDebited) {
        this.autoPenaltyDebited = autoPenaltyDebited;
    }

    public Integer getTotalWorkMinutes() {
        return totalWorkMinutes;
    }

    public void setTotalWorkMinutes(Integer totalWorkMinutes) {
        this.totalWorkMinutes = totalWorkMinutes;
    }

    /**
     * The only way detailStatus should ever be set - keeps the coarse,
     * payroll-facing `status` column deterministically in sync with it
     * rather than letting a caller set one without the other.
     */
    public void applyDetail(AttendanceDetailStatus detailStatus, String remarks, LeaveApplication leaveApplication,
                             TourRequest tourRequest) {
        this.detailStatus = detailStatus;
        this.remarks = remarks;
        this.leaveApplication = leaveApplication;
        this.tourRequest = tourRequest;
        this.status = toCoarseStatus(detailStatus);
    }

    /**
     * Deterministic detail -> coarse mapping (D1): PayrollComputationService
     * and PayrollReportingService keep reading only the coarse AttendanceStatus
     * this produces, so their LOP/breakdown logic needs zero changes. Any late
     * arrival/short-hours/regularization/unauthorized-late consequence is
     * captured as a leave-ledger transaction (Phase C), not as LOP - the
     * employee did physically attend.
     */
    public static AttendanceStatus toCoarseStatus(AttendanceDetailStatus detailStatus) {
        return switch (detailStatus) {
            case PRESENT, GRACE_APPLIED, LATE_SHORT_HOURS, REQUIRES_REGULARIZATION, UNAUTHORIZED_LATE, ON_TOUR, IN_PROGRESS ->
                    AttendanceStatus.PRESENT;
            case HALF_DAY_PRESENT, HALF_DAY_SHORT, HALF_DAY_ABSENT -> AttendanceStatus.HALF_DAY;
            case ABSENT -> AttendanceStatus.ABSENT;
            case ON_LEAVE -> AttendanceStatus.ON_LEAVE;
            case HOLIDAY -> AttendanceStatus.HOLIDAY;
            case WEEKOFF -> AttendanceStatus.WEEKLY_OFF;
        };
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "DailyAttendance";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("attendanceDate", attendanceDate);
        snapshot.put("status", status);
        snapshot.put("inTime", inTime);
        snapshot.put("outTime", outTime);
        snapshot.put("totalWorkingHours", totalWorkingHours);
        snapshot.put("detailStatus", detailStatus);
        snapshot.put("remarks", remarks);
        snapshot.put("leaveApplicationId", leaveApplication != null ? leaveApplication.getId() : null);
        snapshot.put("tourRequestId", tourRequest != null ? tourRequest.getId() : null);
        return snapshot;
    }
}
