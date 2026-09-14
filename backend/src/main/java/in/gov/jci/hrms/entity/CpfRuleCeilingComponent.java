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

/** One typed ceiling-formula component - binds to the already-live cpf_rule_ceiling_component (14 seeded rows). See CpfWithdrawalRuleDetail's own javadoc for why every rule's components combine via MIN. */
@Entity
@Table(name = "cpf_rule_ceiling_component")
public class CpfRuleCeilingComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_id", nullable = false)
    private CpfWithdrawalRuleDetail detail;

    @Column(name = "component_name", nullable = false, length = 64)
    private String componentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_metric", nullable = false, length = 64)
    private CpfCeilingSourceMetric sourceMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 32)
    private CpfCeilingOperator operator;

    @Column(name = "factor_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal factorValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 1;

    protected CpfRuleCeilingComponent() {
    }

    public CpfRuleCeilingComponent(CpfWithdrawalRuleDetail detail, String componentName, CpfCeilingSourceMetric sourceMetric,
                                    CpfCeilingOperator operator, BigDecimal factorValue, int displayOrder) {
        this.detail = detail;
        this.componentName = componentName;
        this.sourceMetric = sourceMetric;
        this.operator = operator;
        this.factorValue = factorValue;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public CpfWithdrawalRuleDetail getDetail() {
        return detail;
    }

    public String getComponentName() {
        return componentName;
    }

    public CpfCeilingSourceMetric getSourceMetric() {
        return sourceMetric;
    }

    public CpfCeilingOperator getOperator() {
        return operator;
    }

    public BigDecimal getFactorValue() {
        return factorValue;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}
