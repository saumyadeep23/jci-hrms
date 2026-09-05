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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An OUTSOURCED employee's historized deployment ledger (V50) - one row per deployment/renewal,
 * only one is_current=true row per employee (idx_uq_current_outsourced_deployment). Additive
 * alongside employee_employment_categories.monthly_ctc (V29) - see that migration's header for why
 * both exist.
 */
@Entity
@Table(name = "outsourced_deployments")
@EntityListeners(AuditableEntityListener.class)
public class OutsourcedDeployment implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private VendorMaster vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_code", referencedColumnName = "scale_code")
    private GradeScaleMaster gradeScale;

    @Column(name = "monthly_ctc", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyCtc;

    @Column(name = "agency_billing_rate", precision = 12, scale = 2)
    private BigDecimal agencyBillingRate;

    @Column(name = "deployment_start_date", nullable = false)
    private LocalDate deploymentStartDate;

    @Column(name = "deployment_end_date", nullable = false)
    private LocalDate deploymentEndDate;

    @Column(name = "work_order_ref", nullable = false, length = 100)
    private String workOrderRef;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected OutsourcedDeployment() {
    }

    public OutsourcedDeployment(Employee employee, BigDecimal monthlyCtc, LocalDate deploymentStartDate,
                                 LocalDate deploymentEndDate, String workOrderRef) {
        this.employee = employee;
        this.monthlyCtc = monthlyCtc;
        this.deploymentStartDate = deploymentStartDate;
        this.deploymentEndDate = deploymentEndDate;
        this.workOrderRef = workOrderRef;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public VendorMaster getVendor() {
        return vendor;
    }

    public void setVendor(VendorMaster vendor) {
        this.vendor = vendor;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public void setGradeScale(GradeScaleMaster gradeScale) {
        this.gradeScale = gradeScale;
    }

    public BigDecimal getMonthlyCtc() {
        return monthlyCtc;
    }

    public BigDecimal getAgencyBillingRate() {
        return agencyBillingRate;
    }

    public void setAgencyBillingRate(BigDecimal agencyBillingRate) {
        this.agencyBillingRate = agencyBillingRate;
    }

    public LocalDate getDeploymentStartDate() {
        return deploymentStartDate;
    }

    public LocalDate getDeploymentEndDate() {
        return deploymentEndDate;
    }

    public String getWorkOrderRef() {
        return workOrderRef;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    @Override
    public String auditEntityName() {
        return "OutsourcedDeployment";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("vendorId", vendor != null ? vendor.getId() : null);
        snapshot.put("monthlyCtc", monthlyCtc);
        snapshot.put("current", current);
        return snapshot;
    }
}
