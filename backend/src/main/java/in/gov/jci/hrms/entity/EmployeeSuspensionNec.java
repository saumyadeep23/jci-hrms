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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One month's Non-Employment Certificate submission/verification for one suspension - the monthly gate
 * PayrollBatchComputationService checks before releasing that month's subsistence allowance (Head 66).
 * uq_suspension_nec_month (suspension_id, sal_month, sal_year) guarantees at most one row per month.
 */
@Entity
@Table(name = "employee_suspension_nec")
public class EmployeeSuspensionNec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suspension_id", nullable = false)
    private EmployeeSuspensionRecord suspension;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "sal_month", nullable = false)
    private int salMonth;

    @Column(name = "sal_year", nullable = false)
    private int salYear;

    @Column(name = "nec_submitted", nullable = false)
    private boolean necSubmitted = false;

    @Column(name = "submission_date")
    private LocalDate submissionDate;

    @Column(name = "doc_reference", length = 100)
    private String docReference;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private Employee verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected EmployeeSuspensionNec() {
    }

    public EmployeeSuspensionNec(EmployeeSuspensionRecord suspension, Employee employee, int salMonth, int salYear) {
        this.suspension = suspension;
        this.employee = employee;
        this.salMonth = salMonth;
        this.salYear = salYear;
    }

    public Long getId() {
        return id;
    }

    public EmployeeSuspensionRecord getSuspension() {
        return suspension;
    }

    public Employee getEmployee() {
        return employee;
    }

    public int getSalMonth() {
        return salMonth;
    }

    public int getSalYear() {
        return salYear;
    }

    public boolean isNecSubmitted() {
        return necSubmitted;
    }

    public void setNecSubmitted(boolean necSubmitted) {
        this.necSubmitted = necSubmitted;
    }

    public LocalDate getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(LocalDate submissionDate) {
        this.submissionDate = submissionDate;
    }

    public String getDocReference() {
        return docReference;
    }

    public void setDocReference(String docReference) {
        this.docReference = docReference;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Employee getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(Employee verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
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
}
