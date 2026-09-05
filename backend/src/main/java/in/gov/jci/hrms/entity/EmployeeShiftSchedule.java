package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * An explicit per-employee/day roster override, consulted by
 * ShiftResolutionService before it falls back to the office-based default
 * (see that class's javadoc). shift == null on an existing row is a
 * deliberate WEEKLY_OFF override (e.g. a compensatory off) - distinct from
 * no row at all, which just means "use the default for this office type".
 *
 * Nothing writes to this table yet - there is no roster-assignment admin UI
 * (DutyRosterPage.tsx currently only shows the read-only shift catalog and
 * says as much) - so this entity exists for ShiftResolutionService's read
 * path to be complete, not because rows are expected today.
 */
@Entity
@Table(name = "employee_shift_schedule")
public class EmployeeShiftSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "schedule_date", nullable = false)
    private LocalDate scheduleDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private ShiftMaster shift;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmployeeShiftSchedule() {
    }

    public EmployeeShiftSchedule(Employee employee, LocalDate scheduleDate, ShiftMaster shift) {
        this.employee = employee;
        this.scheduleDate = scheduleDate;
        this.shift = shift;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public ShiftMaster getShift() {
        return shift;
    }

    public void setShift(ShiftMaster shift) {
        this.shift = shift;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
