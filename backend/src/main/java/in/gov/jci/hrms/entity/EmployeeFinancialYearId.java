package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite key (employee_id, financial_year) for ViewEmployeeTaxYtdAggregate - the view has no single-column primary key of its own. */
@Embeddable
public class EmployeeFinancialYearId implements Serializable {

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "financial_year")
    private String financialYear;

    protected EmployeeFinancialYearId() {
    }

    public EmployeeFinancialYearId(Long employeeId, String financialYear) {
        this.employeeId = employeeId;
        this.financialYear = financialYear;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getFinancialYear() {
        return financialYear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeFinancialYearId that)) return false;
        return Objects.equals(employeeId, that.employeeId) && Objects.equals(financialYear, that.financialYear);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId, financialYear);
    }
}
