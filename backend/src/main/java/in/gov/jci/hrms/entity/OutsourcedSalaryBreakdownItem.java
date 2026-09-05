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

import java.math.BigDecimal;
import java.time.Instant;

/** CTC line item for an OUTSOURCED employee - PIMS_SPEC.md Step 6. */
@Entity
@Table(name = "outsourced_salary_breakdown")
public class OutsourcedSalaryBreakdownItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "head_code", nullable = false, length = 20)
    private String headCode;

    @Column(name = "head_name", nullable = false, length = 100)
    private String headName;

    @Enumerated(EnumType.STRING)
    @Column(name = "head_type", nullable = false, length = 20)
    private SalaryBreakdownHeadType headType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OutsourcedSalaryBreakdownItem() {
    }

    public OutsourcedSalaryBreakdownItem(Employee employee, String headCode, String headName,
                                          SalaryBreakdownHeadType headType, BigDecimal amount) {
        this.employee = employee;
        this.headCode = headCode;
        this.headName = headName;
        this.headType = headType;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getHeadCode() {
        return headCode;
    }

    public String getHeadName() {
        return headName;
    }

    public SalaryBreakdownHeadType getHeadType() {
        return headType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
