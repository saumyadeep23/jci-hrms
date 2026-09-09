package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Salary Heads Directory (JCI Payroll Engine) - the fixed 61-head catalog, mapping the pre-existing
 * payroll_salary_heads table (created directly against the shared dev database before this entity
 * existed - see V66's own comment). head_count is the table's own primary key, not a surrogate id or
 * a reorderable sequence - payroll_monthly_head_items/payroll_monthly_adjustments already FK to it
 * directly, so it is never reassigned once a head is seeded. Only ref_account_code, sal_slip_vis, and
 * is_variable are editable via the API - see PayrollMasterController/SalaryHeadUpdateRequest.
 */
@Entity
@Table(name = "payroll_salary_heads")
@EntityListeners(AuditableEntityListener.class)
public class SalaryHead implements Auditable {

    @Id
    @Column(name = "head_count")
    private Integer headCount;

    @Column(name = "description", nullable = false, length = 150)
    private String description;

    @Column(name = "short_name", nullable = false, length = 50)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type", nullable = false, length = 20)
    private SalaryHeadEffectType effectType;

    @Column(name = "is_variable", nullable = false)
    private boolean variable;

    @Column(name = "applicable_for", nullable = false, length = 20)
    private String applicableFor = "REGULAR";

    @Column(name = "sal_slip_vis")
    private Integer salSlipVis;

    @Column(name = "basic_dependent", nullable = false)
    private boolean basicDependent;

    /** Not part of the original live table - added by V66 for GL integration; NULL until an admin fills it in. */
    @Column(name = "ref_account_code", length = 30)
    private String refAccountCode;

    protected SalaryHead() {
    }

    public Integer getHeadCount() {
        return headCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public SalaryHeadEffectType getEffectType() {
        return effectType;
    }

    public void setEffectType(SalaryHeadEffectType effectType) {
        this.effectType = effectType;
    }

    public boolean isVariable() {
        return variable;
    }

    public void setVariable(boolean variable) {
        this.variable = variable;
    }

    public String getApplicableFor() {
        return applicableFor;
    }

    public void setApplicableFor(String applicableFor) {
        this.applicableFor = applicableFor;
    }

    public Integer getSalSlipVis() {
        return salSlipVis;
    }

    public void setSalSlipVis(Integer salSlipVis) {
        this.salSlipVis = salSlipVis;
    }

    public boolean isBasicDependent() {
        return basicDependent;
    }

    public void setBasicDependent(boolean basicDependent) {
        this.basicDependent = basicDependent;
    }

    public String getRefAccountCode() {
        return refAccountCode;
    }

    public void setRefAccountCode(String refAccountCode) {
        this.refAccountCode = refAccountCode;
    }

    @Override
    public String auditEntityName() {
        return "SalaryHead";
    }

    @Override
    public Long auditEntityId() {
        return headCount != null ? headCount.longValue() : null;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("headCount", headCount);
        snapshot.put("refAccountCode", refAccountCode);
        snapshot.put("salSlipVis", salSlipVis);
        snapshot.put("variable", variable);
        return snapshot;
    }
}
