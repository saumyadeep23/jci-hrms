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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * No updated_at/deleted_at column - a diversion is recorded once and only
 * transitions ACTIVE -> SETTLED in place, never edited or soft-deleted.
 */
@Entity
@Table(name = "pf_diversions")
@EntityListeners(AuditableEntityListener.class)
public class PfDiversion implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "diversion_type", nullable = false, length = 30, columnDefinition = "VARCHAR")
    private DiversionType diversionType;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "emp_bucket_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal empBucketAmount = BigDecimal.ZERO;

    @Column(name = "er_bucket_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal erBucketAmount = BigDecimal.ZERO;

    @Column(name = "vpf_bucket_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal vpfBucketAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_loan_id")
    private EmployeeLoan linkedLoan;

    @Column(name = "sanction_date", nullable = false)
    private LocalDate sanctionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private DiversionStatus status = DiversionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PfDiversion() {
    }

    public PfDiversion(Employee employee, DiversionType diversionType, BigDecimal totalAmount,
                        BigDecimal empBucketAmount, BigDecimal erBucketAmount, BigDecimal vpfBucketAmount,
                        EmployeeLoan linkedLoan, LocalDate sanctionDate) {
        this.employee = employee;
        this.diversionType = diversionType;
        this.totalAmount = totalAmount;
        this.empBucketAmount = empBucketAmount;
        this.erBucketAmount = erBucketAmount;
        this.vpfBucketAmount = vpfBucketAmount;
        this.linkedLoan = linkedLoan;
        this.sanctionDate = sanctionDate;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public DiversionType getDiversionType() {
        return diversionType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getEmpBucketAmount() {
        return empBucketAmount;
    }

    public BigDecimal getErBucketAmount() {
        return erBucketAmount;
    }

    public BigDecimal getVpfBucketAmount() {
        return vpfBucketAmount;
    }

    public EmployeeLoan getLinkedLoan() {
        return linkedLoan;
    }

    public LocalDate getSanctionDate() {
        return sanctionDate;
    }

    public DiversionStatus getStatus() {
        return status;
    }

    public void setStatus(DiversionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "PfDiversion";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("diversionType", diversionType);
        snapshot.put("totalAmount", totalAmount);
        snapshot.put("empBucketAmount", empBucketAmount);
        snapshot.put("erBucketAmount", erBucketAmount);
        snapshot.put("vpfBucketAmount", vpfBucketAmount);
        snapshot.put("linkedLoanId", linkedLoan != null ? linkedLoan.getId() : null);
        snapshot.put("sanctionDate", sanctionDate);
        snapshot.put("status", status);
        return snapshot;
    }
}
