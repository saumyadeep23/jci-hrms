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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One version of a CPF withdrawal rule for one purpose - binds to the already-live
 * cpf_withdrawal_rule_version (7 seeded rows, version_tag "2026.1" for each - see CpfHeadMaster's own
 * javadoc for the discovery that this schema already existed before any Java code did). Never edited once
 * APPROVED - {@code CpfWithdrawalRuleService.approve()} closes this row's effectiveTo and creates a fresh
 * version rather than mutating a row a transaction may already reference (Part 2 of the spec: "never modify
 * historical rules"). purposeId+effectiveFrom has a live partial-unique index (only one APPROVED row per
 * exact start date) - this class's own service layer additionally ensures only one version is the
 * currently-effective one for a purpose by closing the prior version's effectiveTo when a new one is
 * approved, since the database itself does not structurally enforce that beyond the exact-date collision
 * case.
 */
@Entity
@Table(name = "cpf_withdrawal_rule_version")
public class CpfWithdrawalRuleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purpose_id", nullable = false)
    private CpfWithdrawalPurposeMaster purpose;

    @Column(name = "version_tag", nullable = false, length = 32)
    private String versionTag;

    /** Native Postgres enum column (cpf_rule_status) - NAMED_ENUM tells Hibernate 6 to bind/read it as that DB enum type rather than plain varchar, avoiding a "column is of type cpf_rule_status but expression is of type character varying" error. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private CpfRuleStatus status = CpfRuleStatus.DRAFT;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "approval_reference", length = 128)
    private String approvalReference;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    protected CpfWithdrawalRuleVersion() {
    }

    public CpfWithdrawalRuleVersion(CpfWithdrawalPurposeMaster purpose, String versionTag, LocalDate effectiveFrom, String changeReason, String createdBy) {
        this.purpose = purpose;
        this.versionTag = versionTag;
        this.effectiveFrom = effectiveFrom;
        this.changeReason = changeReason;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public CpfWithdrawalPurposeMaster getPurpose() {
        return purpose;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public CpfRuleStatus getStatus() {
        return status;
    }

    public void setStatus(CpfRuleStatus status) {
        this.status = status;
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

    public String getApprovalReference() {
        return approvalReference;
    }

    public void setApprovalReference(String approvalReference) {
        this.approvalReference = approvalReference;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(String verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
