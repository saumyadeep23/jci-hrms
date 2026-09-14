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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * cpf_loan_recovery_policy (V84) - the CPF Trust loan repayment-commencement mode and
 * principal-to-interest-installment ratio (spec Parts 15/19), effective-dated with the same
 * "current row has effectiveTo == null; revising closes it and inserts a new one" convention as
 * {@link CpfPayrollDeductionCap} (see CpfPayrollDeductionCapService), for consistency with this
 * codebase's own lighter-weight versioning pattern rather than the heavier
 * CpfWithdrawalRuleVersion/Detail draft-verify-approve workflow (Part 34: minimum necessary schema).
 *
 * <p>A loan snapshots the policy in effect AT SANCTION TIME by baking the resolved ratio directly
 * into its own totalInterestInstallments (see CpfLoanApplicationService.sanctionLoan()) rather than
 * carrying a live FK to this table - so a later policy change never silently rewrites an existing
 * loan's schedule (Part 20/51).
 */
@Entity
@Table(name = "cpf_loan_recovery_policy")
@EntityListeners(AuditableEntityListener.class)
public class CpfLoanRecoveryPolicy implements Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Only NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT is implemented today - see CpfLoanPayrollRecoveryResolverService's own javadoc. */
    @Column(name = "commencement_mode", nullable = false, length = 40)
    private String commencementMode;

    @Column(name = "principal_installments_per_interest_installment", nullable = false)
    private int principalInstallmentsPerInterestInstallment;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remarks")
    private String remarks;

    protected CpfLoanRecoveryPolicy() {
    }

    public CpfLoanRecoveryPolicy(String commencementMode, int principalInstallmentsPerInterestInstallment,
                                  LocalDate effectiveFrom, String remarks) {
        this.commencementMode = commencementMode;
        this.principalInstallmentsPerInterestInstallment = principalInstallmentsPerInterestInstallment;
        this.effectiveFrom = effectiveFrom;
        this.remarks = remarks;
    }

    public Long getId() {
        return id;
    }

    public String getCommencementMode() {
        return commencementMode;
    }

    public int getPrincipalInstallmentsPerInterestInstallment() {
        return principalInstallmentsPerInterestInstallment;
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
        return "CpfLoanRecoveryPolicy";
    }

    @Override
    public Long auditEntityId() {
        return id;
    }

    @Override
    public Map<String, Object> auditSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("commencementMode", commencementMode);
        snapshot.put("principalInstallmentsPerInterestInstallment", principalInstallmentsPerInterestInstallment);
        snapshot.put("effectiveFrom", effectiveFrom);
        snapshot.put("effectiveTo", effectiveTo);
        return snapshot;
    }
}
