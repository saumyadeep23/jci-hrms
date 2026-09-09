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

/** One non-zero salary-head line item (earning or deduction) for one PayrollMonthlyRecord - only non-zero heads are inserted, per PayrollComputationService.processBatch()'s own rule. */
@Entity
@Table(name = "payroll_monthly_head_items")
public class PayrollMonthlyHeadItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tran_id", nullable = false)
    private PayrollMonthlyRecord record;

    @Column(name = "head_count", nullable = false)
    private int headCount;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected PayrollMonthlyHeadItem() {
    }

    public PayrollMonthlyHeadItem(PayrollMonthlyRecord record, int headCount, BigDecimal amount) {
        this.record = record;
        this.headCount = headCount;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public PayrollMonthlyRecord getRecord() {
        return record;
    }

    public int getHeadCount() {
        return headCount;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
