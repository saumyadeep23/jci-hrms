package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;

/**
 * Read-only mapping of view_employee_tax_ytd_aggregates - cumulative actuals per (employee, financial
 * year) the Section 192 TDS engine projects forward from every batch run. {@code @Subselect} (not
 * {@code @Table}) because this is a database VIEW, not a table Hibernate should ever try to
 * insert/update/DDL-validate against; {@code @Immutable} reinforces read-only at the Hibernate level.
 */
@Entity
@Immutable
@Subselect("SELECT * FROM view_employee_tax_ytd_aggregates")
@Table(name = "view_employee_tax_ytd_aggregates")
public class ViewEmployeeTaxYtdAggregate {

    @EmbeddedId
    private EmployeeFinancialYearId id;

    @Column(name = "employee_code")
    private String employeeCode;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "system_months_count")
    private long systemMonthsCount;

    @Column(name = "total_cumulative_gross")
    private BigDecimal totalCumulativeGross;

    @Column(name = "total_cumulative_tds_paid")
    private BigDecimal totalCumulativeTdsPaid;

    @Column(name = "total_cumulative_cpf")
    private BigDecimal totalCumulativeCpf;

    @Column(name = "total_cumulative_ptax")
    private BigDecimal totalCumulativePtax;

    protected ViewEmployeeTaxYtdAggregate() {
    }

    public EmployeeFinancialYearId getId() {
        return id;
    }

    public Long getEmployeeId() {
        return id.getEmployeeId();
    }

    public String getFinancialYear() {
        return id.getFinancialYear();
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPanNumber() {
        return panNumber;
    }

    public long getSystemMonthsCount() {
        return systemMonthsCount;
    }

    public BigDecimal getTotalCumulativeGross() {
        return totalCumulativeGross;
    }

    public BigDecimal getTotalCumulativeTdsPaid() {
        return totalCumulativeTdsPaid;
    }

    public BigDecimal getTotalCumulativeCpf() {
        return totalCumulativeCpf;
    }

    public BigDecimal getTotalCumulativePtax() {
        return totalCumulativePtax;
    }
}
