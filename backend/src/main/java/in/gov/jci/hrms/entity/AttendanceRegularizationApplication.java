package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.time.LocalDate;

/** HoD approval workflow for a REQUIRES_REGULARIZATION/UNAUTHORIZED_LATE day - see AttendanceRegularizationService. */
@Entity
@Table(name = "attendance_regularization_applications")
public class AttendanceRegularizationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_attendance_id")
    private DailyAttendance dailyAttendance;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30)
    private RegularizationReasonCode reasonCode;

    @Column(name = "remarks")
    private String remarks;

    @Column(name = "corrected_in_time")
    private Instant correctedInTime;

    @Column(name = "corrected_out_time")
    private Instant correctedOutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designated_approver_id")
    private Employee designatedApprover;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approver_remarks")
    private String approverRemarks;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceRegularizationApplication() {
    }

    public AttendanceRegularizationApplication(Employee employee, LocalDate attendanceDate, DailyAttendance dailyAttendance,
                                                 RegularizationReasonCode reasonCode, String remarks,
                                                 Instant correctedInTime, Instant correctedOutTime) {
        this.employee = employee;
        this.attendanceDate = attendanceDate;
        this.dailyAttendance = dailyAttendance;
        this.reasonCode = reasonCode;
        this.remarks = remarks;
        this.correctedInTime = correctedInTime;
        this.correctedOutTime = correctedOutTime;
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

    public DailyAttendance getDailyAttendance() {
        return dailyAttendance;
    }

    public RegularizationReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCorrectedInTime() {
        return correctedInTime;
    }

    public Instant getCorrectedOutTime() {
        return correctedOutTime;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Employee getDesignatedApprover() {
        return designatedApprover;
    }

    public void setDesignatedApprover(Employee designatedApprover) {
        this.designatedApprover = designatedApprover;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getApproverRemarks() {
        return approverRemarks;
    }

    public void setApproverRemarks(String approverRemarks) {
        this.approverRemarks = approverRemarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
