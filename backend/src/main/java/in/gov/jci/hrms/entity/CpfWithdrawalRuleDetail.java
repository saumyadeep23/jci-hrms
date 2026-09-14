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
import java.util.UUID;

/**
 * The actual eligibility/ceiling/tenure/interest/tax parameters for one {@link CpfWithdrawalRuleVersion} -
 * binds to the already-live cpf_withdrawal_rule_detail (7 seeded rows - see CpfHeadMaster's own javadoc).
 * One detail row per version (1:1, split out from the version/workflow metadata) - {@code min_service_months}
 * is already expressed as a single month count (not separate years+months fields, unlike an earlier draft of
 * this module). There is no per-rule ceiling-combine-operator column: every live rule's multiple ceiling
 * components are combined by taking their MINIMUM (matching every one of the spec's own worked examples -
 * see CpfWithdrawalRuleEngine's own javadoc for why this is hardcoded as the combiner rather than a stored
 * per-rule choice this schema doesn't expose).
 */
@Entity
@Table(name = "cpf_withdrawal_rule_detail")
public class CpfWithdrawalRuleDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private CpfWithdrawalRuleVersion version;

    @Column(name = "min_service_months", nullable = false)
    private int minServiceMonths;

    @Column(name = "include_previous_service", nullable = false)
    private boolean includePreviousService = true;

    @Column(name = "allow_break_in_service", nullable = false)
    private boolean allowBreakInService = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_scope", nullable = false, length = 32)
    private CpfFrequencyScope frequencyScope = CpfFrequencyScope.NONE;

    @Column(name = "max_occurrences", nullable = false)
    private int maxOccurrences = 1;

    @Column(name = "max_active_concurrency", nullable = false)
    private int maxActiveConcurrency = 1;

    @Column(name = "balance_retention_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal balanceRetentionPct = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_credit_method", nullable = false, length = 32)
    private CpfRepaymentCreditMethod repaymentCreditMethod = CpfRepaymentCreditMethod.ORIGINAL_DEBIT_HEAD;

    @Column(name = "min_tenure_months")
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months")
    private Integer maxTenureMonths;

    @Column(name = "default_tenure_months")
    private Integer defaultTenureMonths;

    @Column(name = "interest_rate_annual", precision = 5, scale = 2)
    private BigDecimal interestRateAnnual;

    /** Reference key naming which EXISTING interest calculation to invoke (Part 17) - "JCI_TRUST_PRINCIPAL_FIRST" means CpfLoanApplicationService's own principal-then-interest advance formula; never a second interest engine. */
    @Column(name = "interest_method", length = 64)
    private String interestMethod;

    @Column(name = "allow_prepayment")
    private Boolean allowPrepayment;

    @Column(name = "allow_conversion")
    private Boolean allowConversion;

    /** A label (e.g. "NORMAL"/"COOPERATIVE") the caller resolves against CpfPayrollDeductionCapService's own normal/cooperative percentages (Part 18) - not a percentage itself. */
    @Column(name = "payroll_cap_type", nullable = false, length = 32)
    private String payrollCapType = "NORMAL";

    @Column(name = "tax_rule_reference", length = 64)
    private String taxRuleReference;

    @Column(name = "tax_service_threshold_months")
    private Integer taxServiceThresholdMonths;

    @Column(name = "workflow_definition_code", nullable = false, length = 64)
    private String workflowDefinitionCode = "CPF_STANDARD_APPROVAL";

    protected CpfWithdrawalRuleDetail() {
    }

    public CpfWithdrawalRuleDetail(CpfWithdrawalRuleVersion version) {
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public CpfWithdrawalRuleVersion getVersion() {
        return version;
    }

    public int getMinServiceMonths() {
        return minServiceMonths;
    }

    public void setMinServiceMonths(int minServiceMonths) {
        this.minServiceMonths = minServiceMonths;
    }

    public boolean isIncludePreviousService() {
        return includePreviousService;
    }

    public void setIncludePreviousService(boolean includePreviousService) {
        this.includePreviousService = includePreviousService;
    }

    public boolean isAllowBreakInService() {
        return allowBreakInService;
    }

    public void setAllowBreakInService(boolean allowBreakInService) {
        this.allowBreakInService = allowBreakInService;
    }

    public CpfFrequencyScope getFrequencyScope() {
        return frequencyScope;
    }

    public void setFrequencyScope(CpfFrequencyScope frequencyScope) {
        this.frequencyScope = frequencyScope;
    }

    public int getMaxOccurrences() {
        return maxOccurrences;
    }

    public void setMaxOccurrences(int maxOccurrences) {
        this.maxOccurrences = maxOccurrences;
    }

    public int getMaxActiveConcurrency() {
        return maxActiveConcurrency;
    }

    public void setMaxActiveConcurrency(int maxActiveConcurrency) {
        this.maxActiveConcurrency = maxActiveConcurrency;
    }

    public BigDecimal getBalanceRetentionPct() {
        return balanceRetentionPct;
    }

    public void setBalanceRetentionPct(BigDecimal balanceRetentionPct) {
        this.balanceRetentionPct = balanceRetentionPct;
    }

    public CpfRepaymentCreditMethod getRepaymentCreditMethod() {
        return repaymentCreditMethod;
    }

    public void setRepaymentCreditMethod(CpfRepaymentCreditMethod repaymentCreditMethod) {
        this.repaymentCreditMethod = repaymentCreditMethod;
    }

    public Integer getMinTenureMonths() {
        return minTenureMonths;
    }

    public void setMinTenureMonths(Integer minTenureMonths) {
        this.minTenureMonths = minTenureMonths;
    }

    public Integer getMaxTenureMonths() {
        return maxTenureMonths;
    }

    public void setMaxTenureMonths(Integer maxTenureMonths) {
        this.maxTenureMonths = maxTenureMonths;
    }

    public Integer getDefaultTenureMonths() {
        return defaultTenureMonths;
    }

    public void setDefaultTenureMonths(Integer defaultTenureMonths) {
        this.defaultTenureMonths = defaultTenureMonths;
    }

    public BigDecimal getInterestRateAnnual() {
        return interestRateAnnual;
    }

    public void setInterestRateAnnual(BigDecimal interestRateAnnual) {
        this.interestRateAnnual = interestRateAnnual;
    }

    public String getInterestMethod() {
        return interestMethod;
    }

    public void setInterestMethod(String interestMethod) {
        this.interestMethod = interestMethod;
    }

    public Boolean getAllowPrepayment() {
        return allowPrepayment;
    }

    public void setAllowPrepayment(Boolean allowPrepayment) {
        this.allowPrepayment = allowPrepayment;
    }

    public Boolean getAllowConversion() {
        return allowConversion;
    }

    public void setAllowConversion(Boolean allowConversion) {
        this.allowConversion = allowConversion;
    }

    public String getPayrollCapType() {
        return payrollCapType;
    }

    public void setPayrollCapType(String payrollCapType) {
        this.payrollCapType = payrollCapType;
    }

    public String getTaxRuleReference() {
        return taxRuleReference;
    }

    public void setTaxRuleReference(String taxRuleReference) {
        this.taxRuleReference = taxRuleReference;
    }

    public Integer getTaxServiceThresholdMonths() {
        return taxServiceThresholdMonths;
    }

    public void setTaxServiceThresholdMonths(Integer taxServiceThresholdMonths) {
        this.taxServiceThresholdMonths = taxServiceThresholdMonths;
    }

    public String getWorkflowDefinitionCode() {
        return workflowDefinitionCode;
    }

    public void setWorkflowDefinitionCode(String workflowDefinitionCode) {
        this.workflowDefinitionCode = workflowDefinitionCode;
    }
}
