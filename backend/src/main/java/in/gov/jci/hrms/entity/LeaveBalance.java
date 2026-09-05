package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row per (employee, leave type, year) - enforced by a unique
 * constraint in the V7 migration. No created_at/deleted_at column: a
 * balance is provisioned once (typically by an annual HR/payroll credit
 * process, not by this app) and only ever updated in place thereafter.
 * available balance = creditedDays - usedDays - reservedDays.
 */
@Entity
@Table(name = "leave_balances")
@EntityListeners(AuditableEntityListener.class)
public class LeaveBalance implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "credited_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal creditedDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "reserved_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal reservedDays = BigDecimal.ZERO;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LeaveBalance() {
    }

    public LeaveBalance(Employee employee, LeaveType leaveType, Integer year, BigDecimal creditedDays) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.year = year;
        this.creditedDays = creditedDays;
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

    public Integer getYear() {
        return year;
    }

    public BigDecimal getCreditedDays() {
        return creditedDays;
    }

    public void setCreditedDays(BigDecimal creditedDays) {
        this.creditedDays = creditedDays;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public void setUsedDays(BigDecimal usedDays) {
        this.usedDays = usedDays;
    }

    public BigDecimal getReservedDays() {
        return reservedDays;
    }

    public void setReservedDays(BigDecimal reservedDays) {
        this.reservedDays = reservedDays;
    }

    public BigDecimal getAvailableDays() {
        return creditedDays.subtract(usedDays).subtract(reservedDays);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "LeaveBalance";
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
        snapshot.put("year", year);
        snapshot.put("creditedDays", creditedDays);
        snapshot.put("usedDays", usedDays);
        snapshot.put("reservedDays", reservedDays);
        return snapshot;
    }
}
