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
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * approverPost/approverEmployee are the supervisor SupervisorResolutionService
 * dynamically resolved when this application was submitted (FR-DOA.5/6) -
 * not necessarily who ultimately clicks approve/reject, since there's no
 * authentication in this app yet to verify that.
 */
@Entity
@Table(name = "leave_applications")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditableEntityListener.class)
public class LeaveApplication implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal totalDays;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LeaveApplicationStatus status = LeaveApplicationStatus.DRAFT;

    /** FIRST_HALF/SECOND_HALF is only meaningful for CL - LeaveValidationService (Phase D) enforces that, not a DB constraint (a CHECK can't reference leave_types.code). */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_session", nullable = false, length = 12)
    private LeaveSession leaveSession = LeaveSession.FULL_DAY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_post_id")
    private PostMaster approverPost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_employee_id")
    private Employee approverEmployee;

    /** Whose desk the file is on right now - starts as approverEmployee at submit() time, then moves with every forward(). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_assigned_to")
    private Employee currentAssignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_stage", nullable = false, length = 20)
    private LeaveWorkflowStage workflowStage = LeaveWorkflowStage.SUBMITTED;

    /** Links a same-day-split or contiguous CL+RH pair submitted together (CombinedLeaveApplicationService) - null for every ordinary standalone application. */
    @Column(name = "group_application_id")
    private UUID groupApplicationId;

    /** Which holidays row (holiday_type = RESTRICTED) an RH application observes - see V36's reconciliation note on rh_entry_id -> holidays. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rh_entry_id")
    private Holiday rhEntry;

    /** EL-only: how this application's totalDays split against the enjoyable/encashable entitlement sub-ledgers (Favorable Preservation debit order) - null for every non-EL application. */
    @Column(name = "debited_enjoyable_days", precision = 5, scale = 2)
    private BigDecimal debitedEnjoyableDays;

    @Column(name = "debited_encashable_days", precision = 5, scale = 2)
    private BigDecimal debitedEncashableDays;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected LeaveApplication() {
    }

    public LeaveApplication(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                             BigDecimal totalDays, String reason) {
        this(employee, leaveType, startDate, endDate, totalDays, reason, LeaveSession.FULL_DAY);
    }

    public LeaveApplication(Employee employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate,
                             BigDecimal totalDays, String reason, LeaveSession leaveSession) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalDays = totalDays;
        this.reason = reason;
        this.leaveSession = leaveSession;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    /** Only ever called on a DRAFT application - see LeaveApplicationService.update(). */
    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(BigDecimal totalDays) {
        this.totalDays = totalDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveApplicationStatus status) {
        this.status = status;
    }

    public LeaveSession getLeaveSession() {
        return leaveSession;
    }

    public void setLeaveSession(LeaveSession leaveSession) {
        this.leaveSession = leaveSession;
    }

    public PostMaster getApproverPost() {
        return approverPost;
    }

    public void setApproverPost(PostMaster approverPost) {
        this.approverPost = approverPost;
    }

    public Employee getApproverEmployee() {
        return approverEmployee;
    }

    public void setApproverEmployee(Employee approverEmployee) {
        this.approverEmployee = approverEmployee;
    }

    public Employee getCurrentAssignedTo() {
        return currentAssignedTo;
    }

    public void setCurrentAssignedTo(Employee currentAssignedTo) {
        this.currentAssignedTo = currentAssignedTo;
    }

    public LeaveWorkflowStage getWorkflowStage() {
        return workflowStage;
    }

    public void setWorkflowStage(LeaveWorkflowStage workflowStage) {
        this.workflowStage = workflowStage;
    }

    public UUID getGroupApplicationId() {
        return groupApplicationId;
    }

    public void setGroupApplicationId(UUID groupApplicationId) {
        this.groupApplicationId = groupApplicationId;
    }

    public Holiday getRhEntry() {
        return rhEntry;
    }

    public void setRhEntry(Holiday rhEntry) {
        this.rhEntry = rhEntry;
    }

    public BigDecimal getDebitedEnjoyableDays() {
        return debitedEnjoyableDays;
    }

    public void setDebitedEnjoyableDays(BigDecimal debitedEnjoyableDays) {
        this.debitedEnjoyableDays = debitedEnjoyableDays;
    }

    public BigDecimal getDebitedEncashableDays() {
        return debitedEncashableDays;
    }

    public void setDebitedEncashableDays(BigDecimal debitedEncashableDays) {
        this.debitedEncashableDays = debitedEncashableDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public String auditEntityName() {
        return "LeaveApplication";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("leaveTypeId", leaveType != null ? leaveType.getId() : null);
        snapshot.put("startDate", startDate);
        snapshot.put("endDate", endDate);
        snapshot.put("totalDays", totalDays);
        snapshot.put("status", status);
        snapshot.put("leaveSession", leaveSession);
        snapshot.put("approverPostId", approverPost != null ? approverPost.getId() : null);
        snapshot.put("approverEmployeeId", approverEmployee != null ? approverEmployee.getId() : null);
        snapshot.put("groupApplicationId", groupApplicationId);
        snapshot.put("rhEntryId", rhEntry != null ? rhEntry.getId() : null);
        snapshot.put("deletedAt", deletedAt);
        return snapshot;
    }
}
