package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One mutable row per {@link JciEccsLoanProductCode} (product_code/loan_prefix are blanket-unique in the
 * DB - no revision history table; effective_from/effective_to describe this single row's own validity
 * window, updated in place when configuration changes). Binds to the already-live jcieccs_loan_product
 * (V87), seeded with TERM (prefix TE, ceiling 500000) and EMERGENCY (prefix EM, ceiling 70000).
 */
@Entity
@Table(name = "jcieccs_loan_product")
public class JciEccsLoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_code", nullable = false, length = 20)
    private JciEccsLoanProductCode productCode;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(name = "loan_prefix", nullable = false, length = 5)
    private String loanPrefix;

    @Column(name = "max_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "max_tenure_months", nullable = false)
    private int maxTenureMonths;

    /** "DISBURSEMENT_CYCLE" (Term) or "NEXT_CYCLE" (Emergency) - free text at the DB level, read by
     * JciEccsAmortizationService to pick CycleResolverService.repaymentStartCycle's boolean arg. */
    @Column(name = "repayment_start_rule", nullable = false, length = 40)
    private String repaymentStartRule;

    @Column(name = "interest_method", nullable = false, length = 40)
    private String interestMethod = "REDUCING_MONTHLY";

    @Column(name = "principal_multiple", nullable = false)
    private int principalMultiple = 10;

    @Column(name = "topup_allowed", nullable = false)
    private boolean topupAllowed;

    @Column(name = "topup_min_repaid_pct", precision = 5, scale = 2)
    private BigDecimal topupMinRepaidPct;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JciEccsLoanProduct() {
    }

    public Long getId() {
        return id;
    }

    public JciEccsLoanProductCode getProductCode() {
        return productCode;
    }

    public String getProductName() {
        return productName;
    }

    public String getLoanPrefix() {
        return loanPrefix;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public int getMaxTenureMonths() {
        return maxTenureMonths;
    }

    public String getRepaymentStartRule() {
        return repaymentStartRule;
    }

    public boolean isRepaymentStartsInDisbursementCycle() {
        return "DISBURSEMENT_CYCLE".equals(repaymentStartRule);
    }

    public String getInterestMethod() {
        return interestMethod;
    }

    public int getPrincipalMultiple() {
        return principalMultiple;
    }

    public boolean isTopupAllowed() {
        return topupAllowed;
    }

    public BigDecimal getTopupMinRepaidPct() {
        return topupMinRepaidPct;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
