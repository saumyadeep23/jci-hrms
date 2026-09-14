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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A rule-driven CPF withdrawal/loan application - binds to the already-live cpf_application (0 rows so far -
 * see CpfHeadMaster's own javadoc for how this schema was discovered). Deliberately a NEW, separate table
 * from the pre-existing {@code cpf_loan_applications}/{@link CpfLoanApplication} (which this change does
 * NOT modify) rather than retrofitted onto it - this table already existed, purpose-built for the rule
 * engine (rule_version_id, eligible_amount, calculation_trace), before any Java code referenced it.
 * employeeCode is a plain string (this table has no FK to {@code employees}) - resolve the actual
 * {@link Employee} row via {@code EmployeeRepository.findByEmployeeCode} when needed.
 *
 * <p>Disbursing an application posts BOTH a {@link CpfApplicationLedgerAllocation} row per head (this
 * table's own per-application breakdown/audit view) AND a LOAN_WITHDRAWAL entry to
 * {@code cpf_trust_member_ledger_entries} (the one authoritative running-balance ledger this codebase
 * already has) - never a second, competing balance engine (Part 34 of the spec).
 */
@Entity
@Table(name = "cpf_application")
public class CpfApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_number", nullable = false, unique = true, length = 64)
    private String applicationNumber;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purpose_id", nullable = false)
    private CpfWithdrawalPurposeMaster purpose;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_version_id", nullable = false)
    private CpfWithdrawalRuleVersion ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private CpfApplicationStatus status = CpfApplicationStatus.APPLIED;

    @Column(name = "applied_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "eligible_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal eligibleAmount;

    @Column(name = "sanctioned_amount", precision = 14, scale = 2)
    private BigDecimal sanctionedAmount;

    @Column(name = "tenure_months")
    private Integer tenureMonths;

    @Column(name = "calculated_emi", precision = 14, scale = 2)
    private BigDecimal calculatedEmi;

    /** JSON array of human-readable calculation-trace lines (Part 26) - see CpfWithdrawalRuleEngine.CeilingResult.trace(). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "calculation_trace", nullable = false)
    private String calculationTrace;

    /**
     * JSON array of {@code CpfApplicationDocumentSubmission} (documentName/s3Key/originalFilename) - what
     * the applicant actually submitted, checked at apply() time against this purpose's own
     * {@code CpfRuleDocument} mandatory-document list. Same manual-(de)serialize-via-ObjectMapper
     * convention as calculationTrace above, for consistency rather than introducing a second JSON-mapping
     * style on this same table.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "submitted_documents", nullable = false)
    private String submittedDocuments = "[]";

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "sanctioned_at")
    private Instant sanctionedAt;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    protected CpfApplication() {
    }

    public CpfApplication(String applicationNumber, String employeeCode, CpfWithdrawalPurposeMaster purpose,
                           CpfWithdrawalRuleVersion ruleVersion, BigDecimal appliedAmount, BigDecimal eligibleAmount, String calculationTrace) {
        this.applicationNumber = applicationNumber;
        this.employeeCode = employeeCode;
        this.purpose = purpose;
        this.ruleVersion = ruleVersion;
        this.appliedAmount = appliedAmount;
        this.eligibleAmount = eligibleAmount;
        this.calculationTrace = calculationTrace;
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public CpfWithdrawalPurposeMaster getPurpose() {
        return purpose;
    }

    public CpfWithdrawalRuleVersion getRuleVersion() {
        return ruleVersion;
    }

    public CpfApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(CpfApplicationStatus status) {
        this.status = status;
    }

    public BigDecimal getAppliedAmount() {
        return appliedAmount;
    }

    public BigDecimal getEligibleAmount() {
        return eligibleAmount;
    }

    public BigDecimal getSanctionedAmount() {
        return sanctionedAmount;
    }

    public void setSanctionedAmount(BigDecimal sanctionedAmount) {
        this.sanctionedAmount = sanctionedAmount;
    }

    public Integer getTenureMonths() {
        return tenureMonths;
    }

    public void setTenureMonths(Integer tenureMonths) {
        this.tenureMonths = tenureMonths;
    }

    public BigDecimal getCalculatedEmi() {
        return calculatedEmi;
    }

    public void setCalculatedEmi(BigDecimal calculatedEmi) {
        this.calculatedEmi = calculatedEmi;
    }

    public String getCalculationTrace() {
        return calculationTrace;
    }

    public void setCalculationTrace(String calculationTrace) {
        this.calculationTrace = calculationTrace;
    }

    public String getSubmittedDocuments() {
        return submittedDocuments;
    }

    public void setSubmittedDocuments(String submittedDocuments) {
        this.submittedDocuments = submittedDocuments;
    }

    public Instant getSanctionedAt() {
        return sanctionedAt;
    }

    public void setSanctionedAt(Instant sanctionedAt) {
        this.sanctionedAt = sanctionedAt;
    }

    public Instant getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(Instant disbursedAt) {
        this.disbursedAt = disbursedAt;
    }
}
