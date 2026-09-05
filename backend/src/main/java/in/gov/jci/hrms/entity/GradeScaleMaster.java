package in.gov.jci.hrms.entity;

import in.gov.jci.hrms.audit.Auditable;
import in.gov.jci.hrms.audit.AuditableEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A new, additive grade-scale system (Board/Executive/Staff cadre, 15-grade hierarchy) - see V48
 * migration's own comment for why this exists alongside the pre-existing PayScale/pay_scale_master
 * rather than replacing it (18 dependents, a live SQL view - retirement is a separate later change).
 */
@Entity
@Table(name = "grade_scale_master")
@EntityListeners(AuditableEntityListener.class)
public class GradeScaleMaster implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scale_code", nullable = false, length = 10)
    private String scaleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cadre", nullable = false, length = 20)
    private Cadre cadre;

    @Column(name = "hierarchy_level", nullable = false)
    private int hierarchyLevel;

    @Column(name = "is_board_level", nullable = false)
    private boolean boardLevel;

    @Column(name = "minimum_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumBasic;

    @Column(name = "maximum_basic", nullable = false, precision = 12, scale = 2)
    private BigDecimal maximumBasic;

    @Column(name = "increment_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal incrementRate = new BigDecimal("3.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "scale_type", nullable = false, length = 10)
    private ScaleType scaleType = ScaleType.IDA;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate = LocalDate.of(2017, 1, 1);

    @Column(name = "contractual_lumpsum", precision = 12, scale = 2)
    private BigDecimal contractualLumpsum;

    @Column(name = "outsourced_ctc", precision = 12, scale = 2)
    private BigDecimal outsourcedCtc;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GradeScaleMaster() {
    }

    public GradeScaleMaster(String scaleCode, Cadre cadre, int hierarchyLevel, boolean boardLevel,
                             BigDecimal minimumBasic, BigDecimal maximumBasic) {
        this.scaleCode = scaleCode;
        this.cadre = cadre;
        this.hierarchyLevel = hierarchyLevel;
        this.boardLevel = boardLevel;
        this.minimumBasic = minimumBasic;
        this.maximumBasic = maximumBasic;
    }

    public Long getId() {
        return id;
    }

    public String getScaleCode() {
        return scaleCode;
    }

    public Cadre getCadre() {
        return cadre;
    }

    public void setCadre(Cadre cadre) {
        this.cadre = cadre;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }

    public void setHierarchyLevel(int hierarchyLevel) {
        this.hierarchyLevel = hierarchyLevel;
    }

    public boolean isBoardLevel() {
        return boardLevel;
    }

    public void setBoardLevel(boolean boardLevel) {
        this.boardLevel = boardLevel;
    }

    public BigDecimal getMinimumBasic() {
        return minimumBasic;
    }

    public void setMinimumBasic(BigDecimal minimumBasic) {
        this.minimumBasic = minimumBasic;
    }

    public BigDecimal getMaximumBasic() {
        return maximumBasic;
    }

    public void setMaximumBasic(BigDecimal maximumBasic) {
        this.maximumBasic = maximumBasic;
    }

    public BigDecimal getIncrementRate() {
        return incrementRate;
    }

    public void setIncrementRate(BigDecimal incrementRate) {
        this.incrementRate = incrementRate;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        this.scaleType = scaleType;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public BigDecimal getContractualLumpsum() {
        return contractualLumpsum;
    }

    public void setContractualLumpsum(BigDecimal contractualLumpsum) {
        this.contractualLumpsum = contractualLumpsum;
    }

    public BigDecimal getOutsourcedCtc() {
        return outsourcedCtc;
    }

    public void setOutsourcedCtc(BigDecimal outsourcedCtc) {
        this.outsourcedCtc = outsourcedCtc;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static final java.text.NumberFormat INDIAN_GROUPING =
            java.text.NumberFormat.getNumberInstance(java.util.Locale.of("en", "IN"));

    /** "IDA 1,60,000-2,90,000"-style label (genuine lakh/crore grouping, not Western thousands), matching how a target pay scale is already displayed elsewhere in the movement lifecycle (see PromotionOrderPdfGenerator). */
    public String idaScaleLabel() {
        return scaleType + " " + INDIAN_GROUPING.format(minimumBasic.longValue()) + "-" + INDIAN_GROUPING.format(maximumBasic.longValue());
    }

    @Override
    public String auditEntityName() {
        return "GradeScaleMaster";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scaleCode", scaleCode);
        snapshot.put("contractualLumpsum", contractualLumpsum);
        snapshot.put("outsourcedCtc", outsourcedCtc);
        snapshot.put("active", active);
        return snapshot;
    }
}
