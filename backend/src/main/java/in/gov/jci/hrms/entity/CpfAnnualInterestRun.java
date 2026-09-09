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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One year-end summary row per financial year - maps onto cpf_annual_interest_runs, a table already live
 * on the shared dev database (see V74's own header comment); fin_year is UNIQUE there, so a corrected
 * re-run must update this same row (e.g. moving it to SUPERSEDED) rather than inserting a second row for
 * the same FY - CpfInterestComputationService.computeAnnualInterest() enforces that.
 */
@Entity
@Table(name = "cpf_annual_interest_runs")
public class CpfAnnualInterestRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fin_year", nullable = false, unique = true, length = 9)
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
    private CpfInterestRunStatus status = CpfInterestRunStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by")
    private Employee postedBy;

    @Column(name = "posted_at")
    private Instant postedAt;

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
}
