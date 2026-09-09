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

import java.math.BigDecimal;

/**
 * One non-zero EMPLOYER-side statutory contribution line item (EPF/EPS/NPS employer match) for one
 * PayrollMonthlyRecord - deliberately a separate table from payroll_monthly_head_items, keyed off
 * payroll_statutory_heads rather than payroll_salary_heads, because nothing here is deducted from the
 * employee's own salary and none of it should appear as a payslip line item - see
 * PayrollBatchComputationService.resolveEmployerContributions()'s own javadoc for the computation and
 * stat_head_count mapping. Only non-zero contributions are inserted, same rule as head items.
 */
@Entity
@Table(name = "payroll_monthly_statutory_items")
public class PayrollMonthlyStatutoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tran_id", nullable = false)
    private PayrollMonthlyRecord record;

    @Column(name = "stat_head_count", nullable = false)
    private int statHeadCount;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected PayrollMonthlyStatutoryItem() {
    }

    public PayrollMonthlyStatutoryItem(PayrollMonthlyRecord record, int statHeadCount, BigDecimal amount) {
        this.record = record;
        this.statHeadCount = statHeadCount;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public PayrollMonthlyRecord getRecord() {
        return record;
    }

    public int getStatHeadCount() {
        return statHeadCount;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
