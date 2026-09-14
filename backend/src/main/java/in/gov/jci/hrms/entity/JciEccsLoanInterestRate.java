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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * True effective-dated rate history per {@link JciEccsLoanProduct} - same "current row has effective_to
 * IS NULL, revising closes it and inserts a new one" convention as CpfLoanRecoveryPolicy, except the DB
 * does not enforce single-current-row here (no unique constraint on loan_product_id) - the service layer
 * is the only guard. A loan snapshots whatever rate is current at sanction time onto its own
 * annual_interest_rate column, so later rate revisions never retroactively change an already-sanctioned
 * loan. Binds to the already-live jcieccs_loan_interest_rate (V87).
 */
@Entity
@Table(name = "jcieccs_loan_interest_rate")
public class JciEccsLoanInterestRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_product_id", nullable = false)
    private JciEccsLoanProduct loanProduct;

    @Column(name = "annual_interest_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "approved_reference", length = 200)
    private String approvedReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    protected JciEccsLoanInterestRate() {
    }

    public JciEccsLoanInterestRate(JciEccsLoanProduct loanProduct, BigDecimal annualInterestRate, LocalDate effectiveFrom,
                                    String approvedReference) {
        this.loanProduct = loanProduct;
        this.annualInterestRate = annualInterestRate;
        this.effectiveFrom = effectiveFrom;
        this.approvedReference = approvedReference;
    }

    public Long getId() {
        return id;
    }

    public JciEccsLoanProduct getLoanProduct() {
        return loanProduct;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getApprovedReference() {
        return approvedReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
