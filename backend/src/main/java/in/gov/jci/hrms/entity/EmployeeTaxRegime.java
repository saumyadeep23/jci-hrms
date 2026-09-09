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

import java.time.Instant;

/** One employee's Old/New tax regime election for one financial year - Section 192 defaults to NEW when absent, per CBDT circular. */
@Entity
@Table(name = "employee_tax_regimes")
public class EmployeeTaxRegime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "financial_year", nullable = false, length = 10)
    private String financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_regime", nullable = false, length = 10)
    private TaxRegimeType selectedRegime = TaxRegimeType.NEW;

    @Column(name = "is_locked", nullable = false)
    private boolean locked;

    @Column(name = "opted_at")
    private Instant optedAt;

    @Column(name = "opted_by", length = 100)
    private String optedBy;

    @Column(name = "remarks", length = 255)
    private String remarks;

    protected EmployeeTaxRegime() {
    }

    public EmployeeTaxRegime(Employee employee, String financialYear, TaxRegimeType selectedRegime, String optedBy, String remarks) {
        this.employee = employee;
        this.financialYear = financialYear;
        if (selectedRegime != null) {
            this.selectedRegime = selectedRegime;
        }
        this.optedBy = optedBy;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    public TaxRegimeType getSelectedRegime() {
        return selectedRegime;
    }

    public void setSelectedRegime(TaxRegimeType selectedRegime) {
        this.selectedRegime = selectedRegime;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public Instant getOptedAt() {
        return optedAt;
    }

    public String getOptedBy() {
        return optedBy;
    }

    public String getRemarks() {
        return remarks;
    }
}
