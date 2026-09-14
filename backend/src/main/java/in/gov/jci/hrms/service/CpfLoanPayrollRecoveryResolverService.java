package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Part 13/14 of the spec - the "major missing feature": statutory Heads 30 (CPF Loan Principal
 * Repayment Installment) and 31 (CPF Loan Interest Repayment Installment) were, before this change,
 * only ever READ by {@link CpfLedgerSyncService} - nothing anywhere computed them, so a CPF Trust loan
 * recovered nothing through ordinary monthly payroll unless someone entered those two head amounts by
 * hand. This service is called once per employee from
 * {@link PayrollBatchComputationService}'s own per-employee computation (the existing payroll
 * integration mechanism, Part 13's own wording) to auto-derive that amount, exactly like every other
 * statutory deduction head already computed there - it does not replace or duplicate the payroll
 * engine itself, and touches no other head.
 *
 * <h2>Read-only estimate, not the final posting (Part 17)</h2>
 * The amount resolved here becomes a real {@code payroll_monthly_head_items} row (and therefore
 * reduces the employee's net pay) as soon as the batch is computed - but the loan's own
 * outstandingBalance/outstandingInterest are NOT mutated here. That mutation, and the final say on
 * whether/how much to actually post to the CPF ledger, happens later in
 * {@code CpfLedgerSyncService.applyTwoPhaseLoanRecovery()} at batch-disbursement time, which re-locks
 * and re-reads the loan's live state before posting (Part 17/22) - if the loan closed (e.g. a cash
 * settlement) between this resolution and that posting, the ledger sync records the now-excess amount
 * for CPF Trust staff to trace/refund (Part 26/43) rather than silently dropping it, but it does not
 * retroactively correct the payslip figure already computed here. Re-running payroll computation for a
 * still-open (non-finalized) batch re-resolves this value fresh against the loan's THEN-current state,
 * same as every other head in this engine - there is no separate "recompute" path specific to CPF loans.
 */
@Service
@Transactional(readOnly = true)
public class CpfLoanPayrollRecoveryResolverService {

    private static final List<CpfLoanApplicationStatus> RECOVERABLE_STATUSES = List.of(CpfLoanApplicationStatus.DISBURSED);

    private final CpfLoanApplicationRepository loanRepository;
    private final CpfLoanRecoveryPolicyService recoveryPolicyService;

    public CpfLoanPayrollRecoveryResolverService(CpfLoanApplicationRepository loanRepository, CpfLoanRecoveryPolicyService recoveryPolicyService) {
        this.loanRepository = loanRepository;
        this.recoveryPolicyService = recoveryPolicyService;
    }

    public record RecoveryAmounts(BigDecimal principal, BigDecimal interest) {
        public static final RecoveryAmounts ZERO = new RecoveryAmounts(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** The Head 30/31 amount payroll should deduct for this employee in this batch's payroll period - RecoveryAmounts.ZERO when the employee has no recoverable loan, or the loan isn't yet eligible for this period (Part 15/16). */
    public RecoveryAmounts resolve(Employee employee, PayrollBatch batch) {
        CpfLoanApplication loan = loanRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .filter(l -> RECOVERABLE_STATUSES.contains(l.getStatus()) && l.getRecoveryPhase() != CpfLoanRecoveryPhase.CLOSED)
                .findFirst().orElse(null);
        if (loan == null || !isEligibleForPeriod(loan, batch)) {
            return RecoveryAmounts.ZERO;
        }
        return switch (loan.getRecoveryPhase()) {
            case PRINCIPAL -> new RecoveryAmounts(loan.getMonthlyRecoveryPrincipal().min(loan.getOutstandingBalance()), BigDecimal.ZERO);
            case INTEREST -> new RecoveryAmounts(BigDecimal.ZERO, loan.getMonthlyRecoveryInterest().min(loan.getOutstandingInterest()));
            case CLOSED -> RecoveryAmounts.ZERO;
        };
    }

    /**
     * Part 15/16 - NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT (the only commencement mode this codebase
     * implements - CpfLoanRecoveryPolicyService's own CHECK constraint doesn't allow any other value
     * today): a loan disbursed in calendar month M/Y is eligible for recovery starting the payroll
     * period immediately after M/Y - never the same month, regardless of that batch's own
     * DRAFT/CALCULATED/HR_FINALIZED/DISBURSED status. This is Part 16's own "payroll cutoff" -
     * expressed as a month boundary derived from the disbursement date, not a separately configured
     * day-of-month, since no live rule needs anything finer and Part 2 forbids inventing an unused knob.
     */
    private boolean isEligibleForPeriod(CpfLoanApplication loan, PayrollBatch batch) {
        if (loan.getDisbursedAt() == null) {
            return false;
        }
        String mode = recoveryPolicyService.currentOrThrow().getCommencementMode();
        if (!CpfLoanRecoveryPolicyService.NEXT_PAYROLL_MONTH_AFTER_DISBURSEMENT.equals(mode)) {
            throw new BusinessRuleViolationException(
                    "CPF loan recovery commencement mode " + mode + " is configured but not implemented by this resolver.");
        }
        YearMonth disbursedPeriod = YearMonth.from(loan.getDisbursedAt().atZone(ZoneId.systemDefault()));
        YearMonth batchPeriod = YearMonth.of(batch.getSalYear(), batch.getSalMonth());
        return batchPeriod.isAfter(disbursedPeriod);
    }
}
