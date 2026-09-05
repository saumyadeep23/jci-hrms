package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Maps the V36 table (in.gov.jci.hrms.service.AlmsReportService's javadoc
 * notes no service has ever created a row here - the payroll feed computes
 * its cutoff window on the fly instead). First JPA entity for this table,
 * added only for HolidayMasterService.delete's "don't delete a holiday
 * inside an already-frozen payroll cycle" guard - read-only from this
 * entity's perspective, nothing writes an AttendancePayrollCutoff row.
 */
@Entity
@Table(name = "attendance_payroll_cutoff")
public class AttendancePayrollCutoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cutoff_month", nullable = false)
    private Integer cutoffMonth;

    @Column(name = "cutoff_year", nullable = false)
    private Integer cutoffYear;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "is_frozen", nullable = false)
    private boolean frozen;

    @Column(name = "frozen_at")
    private Instant frozenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AttendancePayrollCutoff() {
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
