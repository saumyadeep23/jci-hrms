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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only transaction log for leave-balance changes driven by the
 * attendance engine (auto-debit for unauthorized lateness) or the Commuted
 * Leave -> HPL debit, mirroring AuditLog's append-only shape - rows are
 * never updated or soft-deleted. Every write to this table happens in the
 * same transaction as the matching LeaveBalance.usedDays mutation it
 * explains, so the two stay reconcilable. Not written to until
 * AttendanceLeaveDeductionService (Phase C) exists - this entity/table ships
 * ahead of that so Phase C is a logic-only diff.
 */
@Entity
@Table(name = "leave_ledger_entries")
public class LeaveLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** Negative for a debit, positive for a credit - e.g. -0.5 for an auto-debit. */
    @Column(name = "delta_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal deltaDays;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private LeaveLedgerSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_daily_attendance_id")
    private DailyAttendance relatedDailyAttendance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_leave_application_id")
    private LeaveApplication relatedLeaveApplication;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaveLedgerEntry() {
    }

    public LeaveLedgerEntry(Employee employee, LeaveType leaveType, LocalDate entryDate, BigDecimal deltaDays,
                             String description, LeaveLedgerSource source) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.entryDate = entryDate;
        this.deltaDays = deltaDays;
        this.description = description;
        this.source = source;
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

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public BigDecimal getDeltaDays() {
        return deltaDays;
    }

    public String getDescription() {
        return description;
    }

    public LeaveLedgerSource getSource() {
        return source;
    }

    public DailyAttendance getRelatedDailyAttendance() {
        return relatedDailyAttendance;
    }

    public void setRelatedDailyAttendance(DailyAttendance relatedDailyAttendance) {
        this.relatedDailyAttendance = relatedDailyAttendance;
    }

    public LeaveApplication getRelatedLeaveApplication() {
        return relatedLeaveApplication;
    }

    public void setRelatedLeaveApplication(LeaveApplication relatedLeaveApplication) {
        this.relatedLeaveApplication = relatedLeaveApplication;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
