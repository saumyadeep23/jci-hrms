package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The JCIECCS collection window: 26th of the previous month to 25th of the current month (cycle_code
 * "YYYY-MM"), resolved by {@code CycleResolverService}. Independent of payroll_batches (calendar-month
 * sal_month/sal_year) and the older payroll_runs - see PayrollBatch's own javadoc for why distinct period
 * concepts stay in distinct tables in this codebase. Binds to the already-live hrms_payroll_cycle (V87).
 */
@Entity
@Table(name = "hrms_payroll_cycle")
public class HrmsPayrollCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_code", nullable = false, length = 7)
    private String cycleCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_status", nullable = false, length = 20)
    private PayrollCycleStatus cycleStatus = PayrollCycleStatus.OPEN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HrmsPayrollCycle() {
    }

    public HrmsPayrollCycle(String cycleCode, LocalDate periodStart, LocalDate periodEnd) {
        this.cycleCode = cycleCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public Long getId() {
        return id;
    }

    public String getCycleCode() {
        return cycleCode;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public PayrollCycleStatus getCycleStatus() {
        return cycleStatus;
    }

    public void setCycleStatus(PayrollCycleStatus cycleStatus) {
        this.cycleStatus = cycleStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
