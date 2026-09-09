package in.gov.jci.hrms.repository;

import in.gov.jci.hrms.entity.EmployeeFinancialYearId;
import in.gov.jci.hrms.entity.ViewEmployeeTaxYtdAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

/** Read-only - view_employee_tax_ytd_aggregates. Look up with findById(new EmployeeFinancialYearId(employeeId, financialYear)). */
public interface ViewEmployeeTaxYtdAggregateRepository extends JpaRepository<ViewEmployeeTaxYtdAggregate, EmployeeFinancialYearId> {
}
