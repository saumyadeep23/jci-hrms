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
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Employee NPS Declaration Desk (JCI Payroll Engine) - one row per (employee, financial year), the
 * declared NPS employee-contribution percentage for that FY. Maps the pre-existing
 * employee_nps_declarations table (see V66's own comment) - status defaults to ACTIVE and declared_by
 * (which admin recorded it, distinct from the employee) is left null since the API doesn't collect it
 * yet. NpsDeclarationServiceImpl enforces the once-per-FY rule at the service layer, not just via the
 * uq_emp_nps_fy database constraint - see its javadoc for why.
 */
@Entity
@Table(name = "employee_nps_declarations")
@EntityListeners(AuditableEntityListener.class)
public class EmployeeNpsDeclaration implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "nps_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal npsPercentage;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declared_by")
    private Employee declaredBy;

    @Column(name = "remarks", length = 255)
    private String remarks;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected EmployeeNpsDeclaration() {
    }

    public EmployeeNpsDeclaration(Employee employee, String financialYear, BigDecimal npsPercentage,
                                   LocalDate effectiveFrom, String remarks) {
        this.employee = employee;
        this.financialYear = financialYear;
        this.npsPercentage = npsPercentage;
        this.effectiveFrom = effectiveFrom;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public BigDecimal getNpsPercentage() {
        return npsPercentage;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getStatus() {
        return status;
    }

    public Employee getDeclaredBy() {
        return declaredBy;
    }

    public String getRemarks() {
        return remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String auditEntityName() {
        return "EmployeeNpsDeclaration";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("financialYear", financialYear);
        snapshot.put("npsPercentage", npsPercentage);
        return snapshot;
    }
}
