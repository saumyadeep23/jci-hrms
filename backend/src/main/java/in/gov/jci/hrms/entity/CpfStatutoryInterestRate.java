package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One officially-notified CPF interest rate for a financial year - maps onto cpf_statutory_interest_rates,
 * a table already live on the shared dev database (created by other tooling before this migration existed
 * - see V77's own header comment); column shapes here match that live table exactly, including its plain
 * UNIQUE(fin_year) constraint (at most one row per FY, not a superseded-history model). Distinct from
 * CpfAnnualInterestRun, which is the execution log of a year-end run that *applies* a rate to every
 * member's ledger; this table is the notification itself, and is what CpfRateResolutionService's Para
 * 60(2) fallback reads.
 */
@Entity
@Table(name = "cpf_statutory_interest_rates")
@EntityListeners(AuditableEntityListener.class)
public class CpfStatutoryInterestRate implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fin_year", nullable = false, length = 9)
    private String finYear;

    @Column(name = "base_cpf_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal baseCpfRate;

    @Column(name = "loan_markup_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal loanMarkupRate;

    @Column(name = "effective_loan_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal effectiveLoanRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDate effectiveTo;

    @Column(name = "ministry_order_no", nullable = false, length = 100)
    private String ministryOrderNo;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CpfStatutoryInterestRate() {
    }

    public CpfStatutoryInterestRate(String finYear, BigDecimal baseCpfRate, BigDecimal loanMarkupRate,
                                     String ministryOrderNo, LocalDate orderDate) {
        this.finYear = finYear;
        this.baseCpfRate = baseCpfRate;
        this.loanMarkupRate = loanMarkupRate;
        this.effectiveLoanRate = baseCpfRate.add(loanMarkupRate);
        this.effectiveFrom = LocalDate.of(Integer.parseInt(finYear.substring(0, 4)), 4, 1);
        this.effectiveTo = LocalDate.of(Integer.parseInt(finYear.substring(5, 9)), 3, 31);
        this.ministryOrderNo = ministryOrderNo;
        this.orderDate = orderDate;
    }

    public Long getId() {
        return id;
    }

    public String getFinYear() {
        return finYear;
    }

    public BigDecimal getBaseCpfRate() {
        return baseCpfRate;
    }

    public BigDecimal getLoanMarkupRate() {
        return loanMarkupRate;
    }

    public BigDecimal getEffectiveLoanRate() {
        return effectiveLoanRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public String getMinistryOrderNo() {
        return ministryOrderNo;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String auditEntityName() {
        return "CpfStatutoryInterestRate";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("finYear", finYear);
        snapshot.put("baseCpfRate", baseCpfRate);
        snapshot.put("loanMarkupRate", loanMarkupRate);
        snapshot.put("effectiveLoanRate", effectiveLoanRate);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        snapshot.put("ministryOrderNo", ministryOrderNo);
        snapshot.put("orderDate", orderDate);
        snapshot.put("active", active);
        return snapshot;
    }
}
