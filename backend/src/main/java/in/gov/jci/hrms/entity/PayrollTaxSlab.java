package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** One progressive income-tax bracket for a (financial_year, regime) pair - Section 192's slab table, plus a flat cess_rate applied once on top of the summed slab tax. */
@Entity
@Table(name = "payroll_tax_slabs")
public class PayrollTaxSlab {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "regime", nullable = false, length = 10)
    private TaxRegimeType regime;

    @Column(name = "slab_min", nullable = false, precision = 12, scale = 2)
    private BigDecimal slabMin;

    @Column(name = "slab_max", precision = 12, scale = 2)
    private BigDecimal slabMax;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = new BigDecimal("4.00");

    protected PayrollTaxSlab() {
    }

    public Long getId() {
        return id;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public TaxRegimeType getRegime() {
        return regime;
    }

    public BigDecimal getSlabMin() {
        return slabMin;
    }

    public BigDecimal getSlabMax() {
        return slabMax;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getCessRate() {
        return cessRate;
    }
}
