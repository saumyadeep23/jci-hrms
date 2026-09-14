package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Payroll Deduction Cap (Part 18) - maps onto the new cpf_payroll_deduction_cap (V82), a table this change
 * introduces since no such construct existed anywhere - unlike the withdrawal type/purpose/rule tables,
 * which were already live (see {@link CpfHeadMaster}'s own javadoc). Uses a plain Long/BIGSERIAL id, this
 * codebase's usual convention, since there was no pre-existing UUID-keyed shape to match here.
 */
@Entity
@Table(name = "cpf_payroll_deduction_cap")
@EntityListeners(AuditableEntityListener.class)
public class CpfPayrollDeductionCap implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normal_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal normalPercent;

    @Column(name = "cooperative_percent", precision = 5, scale = 2)
    private BigDecimal cooperativePercent;

    @Column(name = "applicability")
    private String applicability;

    @Column(name = "legal_reference")
    private String legalReference;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks")
    private String remarks;

    protected CpfPayrollDeductionCap() {
    }

    public CpfPayrollDeductionCap(BigDecimal normalPercent, BigDecimal cooperativePercent, String applicability,
                                   String legalReference, LocalDate effectiveFrom, String remarks) {
        this.normalPercent = normalPercent;
        this.cooperativePercent = cooperativePercent;
        this.applicability = applicability;
        this.legalReference = legalReference;
        this.effectiveFrom = effectiveFrom;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getNormalPercent() {
        return normalPercent;
    }

    public BigDecimal getCooperativePercent() {
        return cooperativePercent;
    }

    public String getApplicability() {
        return applicability;
    }

    public String getLegalReference() {
        return legalReference;
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

    public String getRemarks() {
        return remarks;
    }

    @Override
    public String auditEntityName() {
        return "CpfPayrollDeductionCap";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("normalPercent", normalPercent);
        snapshot.put("cooperativePercent", cooperativePercent);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        return snapshot;
    }
}
