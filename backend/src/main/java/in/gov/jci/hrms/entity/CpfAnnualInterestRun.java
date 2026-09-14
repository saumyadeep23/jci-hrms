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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One CPF annual-interest run - maps onto cpf_annual_interest_runs (V74, extended by V81 for the full
 * Calculate -> Approve/Post -> Reverse -> Recalculate workflow - see CpfInterestRunService's own javadoc).
 * fin_year is no longer globally UNIQUE (V81 replaced that with two partial unique indexes: at most one
 * *active* - i.e. not REVERSED/FAILED - run per fin_year+scope(+member)), so a fin_year can carry a history
 * of superseded runs across time as long as at most one is active.
 */
@Entity
@Table(name = "cpf_annual_interest_runs")
@EntityListeners(AuditableEntityListener.class)
public class CpfAnnualInterestRun implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fin_year", nullable = false, length = 9)
    private String finYear;

    @Column(name = "declared_interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal declaredInterestRate;

    @Column(name = "interest_order_no", nullable = false, length = 100)
    private String interestOrderNo;

    @Column(name = "interest_order_date", nullable = false)
    private LocalDate interestOrderDate;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate = LocalDate.now();

    @Column(name = "total_members_processed", nullable = false)
    private int totalMembersProcessed;

    @Column(name = "total_interest_credited_ee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInterestCreditedEe = BigDecimal.ZERO;

    @Column(name = "total_interest_credited_er", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInterestCreditedEr = BigDecimal.ZERO;

    @Column(name = "total_interest_credited_vpf", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalInterestCreditedVpf = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private CpfInterestRunStatus status = CpfInterestRunStatus.CALCULATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20, columnDefinition = "VARCHAR")
    private CpfInterestRunScope scope = CpfInterestRunScope.ALL_MEMBERS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_employee_id")
    private Employee memberEmployee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calculated_by")
    private Employee calculatedBy;

    @Column(name = "calculated_at")
    private Instant calculatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    private Employee postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_by")
    private Employee reversedBy;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    /** True when calculatePreview() found a member whose pre-migration opening-balance basis could not be confirmed (see CpfInterestRunService.flagDataReviewRequired()) - posting requires the caller to pass acknowledgeDataReview=true. */
    @Column(name = "data_review_required", nullable = false)
    private boolean dataReviewRequired;

    @Column(name = "remarks")
    private String remarks;

    protected CpfAnnualInterestRun() {
    }

    public CpfAnnualInterestRun(String finYear, BigDecimal declaredInterestRate, String interestOrderNo, LocalDate interestOrderDate) {
        this.finYear = finYear;
        this.declaredInterestRate = declaredInterestRate;
        this.interestOrderNo = interestOrderNo;
        this.interestOrderDate = interestOrderDate;
    }

    public Long getId() {
        return id;
    }

    public String getFinYear() {
        return finYear;
    }

    public BigDecimal getDeclaredInterestRate() {
        return declaredInterestRate;
    }

    public String getInterestOrderNo() {
        return interestOrderNo;
    }

    public LocalDate getInterestOrderDate() {
        return interestOrderDate;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public int getTotalMembersProcessed() {
        return totalMembersProcessed;
    }

    public void setTotalMembersProcessed(int totalMembersProcessed) {
        this.totalMembersProcessed = totalMembersProcessed;
    }

    public BigDecimal getTotalInterestCreditedEe() {
        return totalInterestCreditedEe;
    }

    public void setTotalInterestCreditedEe(BigDecimal totalInterestCreditedEe) {
        this.totalInterestCreditedEe = totalInterestCreditedEe;
    }

    public BigDecimal getTotalInterestCreditedEr() {
        return totalInterestCreditedEr;
    }

    public void setTotalInterestCreditedEr(BigDecimal totalInterestCreditedEr) {
        this.totalInterestCreditedEr = totalInterestCreditedEr;
    }

    public BigDecimal getTotalInterestCreditedVpf() {
        return totalInterestCreditedVpf;
    }

    public void setTotalInterestCreditedVpf(BigDecimal totalInterestCreditedVpf) {
        this.totalInterestCreditedVpf = totalInterestCreditedVpf;
    }

    public CpfInterestRunStatus getStatus() {
        return status;
    }

    public void setStatus(CpfInterestRunStatus status) {
        this.status = status;
    }

    public CpfInterestRunScope getScope() {
        return scope;
    }

    public void setScope(CpfInterestRunScope scope) {
        this.scope = scope;
    }

    public Employee getMemberEmployee() {
        return memberEmployee;
    }

    public void setMemberEmployee(Employee memberEmployee) {
        this.memberEmployee = memberEmployee;
    }

    public Employee getCalculatedBy() {
        return calculatedBy;
    }

    public void setCalculatedBy(Employee calculatedBy) {
        this.calculatedBy = calculatedBy;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public Employee getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(Employee postedBy) {
        this.postedBy = postedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public Employee getReversedBy() {
        return reversedBy;
    }

    public void setReversedBy(Employee reversedBy) {
        this.reversedBy = reversedBy;
    }

    public Instant getReversedAt() {
        return reversedAt;
    }

    public void setReversedAt(Instant reversedAt) {
        this.reversedAt = reversedAt;
    }

    public boolean isDataReviewRequired() {
        return dataReviewRequired;
    }

    public void setDataReviewRequired(boolean dataReviewRequired) {
        this.dataReviewRequired = dataReviewRequired;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String auditEntityName() {
        return "CpfAnnualInterestRun";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("finYear", finYear);
        snapshot.put("scope", scope);
        snapshot.put("memberEmployeeId", memberEmployee != null ? memberEmployee.getId() : null);
        snapshot.put("declaredInterestRate", declaredInterestRate);
        snapshot.put("totalMembersProcessed", totalMembersProcessed);
        snapshot.put("totalInterestCreditedEe", totalInterestCreditedEe);
        snapshot.put("totalInterestCreditedEr", totalInterestCreditedEr);
        snapshot.put("totalInterestCreditedVpf", totalInterestCreditedVpf);
        snapshot.put("status", status);
        snapshot.put("dataReviewRequired", dataReviewRequired);
        snapshot.put("calculatedByEmployeeId", calculatedBy != null ? calculatedBy.getId() : null);
        snapshot.put("calculatedAt", calculatedAt);
        snapshot.put("postedByEmployeeId", postedBy != null ? postedBy.getId() : null);
        snapshot.put("postedAt", postedAt);
        snapshot.put("reversedByEmployeeId", reversedBy != null ? reversedBy.getId() : null);
        snapshot.put("reversedAt", reversedAt);
        snapshot.put("remarks", remarks);
        return snapshot;
    }
}
