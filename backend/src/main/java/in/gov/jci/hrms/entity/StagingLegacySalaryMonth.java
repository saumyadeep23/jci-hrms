package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "staging_legacy_salary_months")
@EntityListeners(AuditableEntityListener.class)
public class StagingLegacySalaryMonth implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 50)
    private String employeeCode;

    @Column(name = "salary_year", nullable = false)
    private Integer salaryYear;

    @Column(name = "salary_month", nullable = false)
    private Integer salaryMonth;

    @Column(name = "basic_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal basicPay;

    @Column(name = "gross_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossEarnings;

    @Column(name = "total_deductions", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions;

    @Column(name = "net_pay", nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR")
    private StagingRowStatus status = StagingRowStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StagingLegacySalaryMonth() {
    }

    public StagingLegacySalaryMonth(String employeeCode, Integer salaryYear, Integer salaryMonth, BigDecimal basicPay,
                                     BigDecimal grossEarnings, BigDecimal totalDeductions, BigDecimal netPay) {
        this.employeeCode = employeeCode;
        this.salaryYear = salaryYear;
        this.salaryMonth = salaryMonth;
        this.basicPay = basicPay;
        this.grossEarnings = grossEarnings;
        this.totalDeductions = totalDeductions;
        this.netPay = netPay;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public Integer getSalaryYear() {
        return salaryYear;
    }

    public Integer getSalaryMonth() {
        return salaryMonth;
    }

    public BigDecimal getBasicPay() {
        return basicPay;
    }

    public BigDecimal getGrossEarnings() {
        return grossEarnings;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public StagingRowStatus getStatus() {
        return status;
    }

    public void setStatus(StagingRowStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "StagingLegacySalaryMonth";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeCode", employeeCode);
        snapshot.put("salaryYear", salaryYear);
        snapshot.put("salaryMonth", salaryMonth);
        snapshot.put("grossEarnings", grossEarnings);
        snapshot.put("totalDeductions", totalDeductions);
        snapshot.put("netPay", netPay);
        snapshot.put("status", status);
        snapshot.put("rejectionReason", rejectionReason);
        return snapshot;
    }
}
