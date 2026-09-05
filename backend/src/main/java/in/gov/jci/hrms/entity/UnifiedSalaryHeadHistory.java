package in.gov.jci.hrms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

/**
 * Maps the read-only v_unified_salary_head_history DB view (V12 migration) -
 * a plain JOIN across payslip_items/payslips/payroll_runs/salary_head_master,
 * not a UNION, since promoted legacy salary months become ordinary
 * payroll_runs/payslips/payslip_items rows distinguished only by run_type/
 * is_migrated. No Auditable/AuditableEntityListener - this is a derived,
 * read-only projection, never written to directly.
 */
@Entity
@Immutable
@Table(name = "v_unified_salary_head_history")
public class UnifiedSalaryHeadHistory {

    @Id
    @Column(name = "payslip_item_id")
    private Long payslipItemId;

    @Column(name = "payroll_run_id")
    private Long payrollRunId;

    @Column(name = "cycle_year")
    private Integer cycleYear;

    @Column(name = "cycle_month")
    private Integer cycleMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", columnDefinition = "VARCHAR")
    private PayrollRunType runType;

    @Column(name = "is_migrated")
    private boolean migrated;

    @Column(name = "payslip_id")
    private Long payslipId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "salary_head_id")
    private Long salaryHeadId;

    @Column(name = "salary_head_code")
    private String salaryHeadCode;

    @Column(name = "salary_head_name")
    private String salaryHeadName;

    @Enumerated(EnumType.STRING)
    @Column(name = "head_type", columnDefinition = "VARCHAR")
    private HeadType headType;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "cause_remarks")
    private String causeRemarks;

    protected UnifiedSalaryHeadHistory() {
    }

    public Long getPayslipItemId() {
        return payslipItemId;
    }

    public Long getPayrollRunId() {
        return payrollRunId;
    }

    public Integer getCycleYear() {
        return cycleYear;
    }

    public Integer getCycleMonth() {
        return cycleMonth;
    }

    public PayrollRunType getRunType() {
        return runType;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public Long getPayslipId() {
        return payslipId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public Long getSalaryHeadId() {
        return salaryHeadId;
    }

    public String getSalaryHeadCode() {
        return salaryHeadCode;
    }

    public String getSalaryHeadName() {
        return salaryHeadName;
    }

    public HeadType getHeadType() {
        return headType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCauseRemarks() {
        return causeRemarks;
    }
}
