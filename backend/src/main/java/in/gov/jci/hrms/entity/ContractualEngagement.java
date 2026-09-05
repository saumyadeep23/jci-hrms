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
 * A CONTRACTUAL employee's historized engagement ledger (V50) - one row per engagement/renewal,
 * only one is_current=true row per employee (idx_uq_current_contractual_engagement). Additive
 * alongside employee_employment_categories.fixed_lump_sum_monthly (V29) - see that migration's
 * header for why both exist.
 */
@Entity
@Table(name = "contractual_engagements")
@EntityListeners(AuditableEntityListener.class)
public class ContractualEngagement implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scale_code", referencedColumnName = "scale_code")
    private GradeScaleMaster gradeScale;

    @Column(name = "monthly_lumpsum", nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyLumpsum;

    @Column(name = "contract_start_date", nullable = false)
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date", nullable = false)
    private LocalDate contractEndDate;

    @Column(name = "approval_ref_no", nullable = false, length = 100)
    private String approvalRefNo;

    @Column(name = "engagement_terms", columnDefinition = "TEXT")
    private String engagementTerms;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ContractualEngagement() {
    }

    public ContractualEngagement(Employee employee, BigDecimal monthlyLumpsum, LocalDate contractStartDate,
                                  LocalDate contractEndDate, String approvalRefNo) {
        this.employee = employee;
        this.monthlyLumpsum = monthlyLumpsum;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.approvalRefNo = approvalRefNo;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public GradeScaleMaster getGradeScale() {
        return gradeScale;
    }

    public void setGradeScale(GradeScaleMaster gradeScale) {
        this.gradeScale = gradeScale;
    }

    public BigDecimal getMonthlyLumpsum() {
        return monthlyLumpsum;
    }

    public LocalDate getContractStartDate() {
        return contractStartDate;
    }

    public LocalDate getContractEndDate() {
        return contractEndDate;
    }

    public String getApprovalRefNo() {
        return approvalRefNo;
    }

    public String getEngagementTerms() {
        return engagementTerms;
    }

    public void setEngagementTerms(String engagementTerms) {
        this.engagementTerms = engagementTerms;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

    @Override
    public String auditEntityName() {
        return "ContractualEngagement";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("employeeId", employee != null ? employee.getId() : null);
        snapshot.put("monthlyLumpsum", monthlyLumpsum);
        snapshot.put("contractEndDate", contractEndDate);
        snapshot.put("current", current);
        return snapshot;
    }
}
