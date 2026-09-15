package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import in.gov.jci.hrms.service.JciEccsIntegrityCheckService.IntegrityCheckResult;
import in.gov.jci.hrms.service.JciEccsIntegrityCheckService.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JCIECCS Lifecycle Engine Phase 5 - the minimum runtime contract for a running loan the authorized
 * administrator inserts directly through pgAdmin (spec sections 7-8). This is deliberately NOT a migration
 * engine: it reconstructs nothing, computes nothing, and never repairs anything it finds wrong - it only
 * answers "can this row safely re-enter the normal lifecycle" so a bad manual insert fails safely and
 * visibly (as a CRITICAL finding) instead of corrupting the next payroll cycle it participates in.
 *
 * <p>Deliberately does NOT reuse {@link JciEccsReconciliationService#reconcileLoan}/
 * {@link JciEccsIntegrityCheckService#checkLoan} - those assume the loan's ENTIRE recovery history lives
 * in JCIECCS (derived outstanding = original sanctioned - posted recovery + posted reversals), which is
 * never true for a migrated loan: its outstanding_principal at migration time is the DBA's own
 * authoritative opening position, carrying prior repayment history JCIECCS never recorded. Running that
 * check against a migrated loan would always misfire a false CRITICAL the moment any principal had already
 * been repaid under the old system.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsMigratedLoanValidationService {

    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final CycleResolverService cycleResolverService;

    public JciEccsMigratedLoanValidationService(JciEccsLoanRepository loanRepository, JciEccsLoanScheduleRepository scheduleRepository,
                                                  CycleResolverService cycleResolverService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.cycleResolverService = cycleResolverService;
    }

    /**
     * Validates the fields a manually-inserted running loan must carry to safely appear in Active Loans,
     * generate its next payroll demand, receive a recovery, be reconciled, be restructured/topped-up (TE),
     * and be evaluated for no-dues settlement (spec section 8). Every violation is reported as a CRITICAL
     * finding; an empty result means the loan is safe to re-enter the normal lifecycle. Never throws for a
     * data problem - a missing/invalid loan id is the only thing that's still a hard failure, since there's
     * nothing to validate.
     */
    public List<IntegrityCheckResult> validate(Long loanId) {
        JciEccsLoan loan = loanRepository.findById(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
        List<IntegrityCheckResult> findings = new ArrayList<>();

        // member exists (guaranteed non-null by the FK/@ManyToOne(optional=false) itself - a row that
        // violated this could never have been inserted) - the check worth making is that the member is
        // usable, not merely present.
        if (loan.getMember().getMembershipStatus() != JciEccsMembershipStatus.ACTIVE) {
            findings.add(finding(loanId, "MIGRATED_LOAN_MEMBER_STATUS",
                    "Loan's member is not ACTIVE - a migrated loan for a suspended/closed member cannot safely resume payroll recovery",
                    JciEccsMembershipStatus.ACTIVE, loan.getMember().getMembershipStatus()));
        }

        // loan type valid: loanProduct is a real, non-null @ManyToOne - nothing further to check beyond
        // its presence, which the FK already guarantees.

        if (loan.getLoanIssueId() == null || loan.getLoanIssueId().isBlank()) {
            findings.add(finding(loanId, "MIGRATED_LOAN_ISSUE_ID", "Loan number (loanIssueId) is missing or blank", "non-blank", loan.getLoanIssueId()));
        }

        // status valid: JciEccsLoanStatus is a DB CHECK-constrained enum column, so any persisted value is
        // one of the known statuses by construction - the runtime-relevant check is that a loan meant to
        // resume normal operation is actually ACTIVE (RESTRUCTURED/CLOSED/DEFAULTED/WRITTEN_OFF loans are
        // not expected to appear in a fresh payroll snapshot at all).
        if (loan.getStatus() != JciEccsLoanStatus.ACTIVE) {
            findings.add(finding(loanId, "MIGRATED_LOAN_STATUS", "Loan status must be ACTIVE to resume the normal lifecycle",
                    JciEccsLoanStatus.ACTIVE, loan.getStatus()));
        }

        if (loan.getOutstandingPrincipal() == null || loan.getOutstandingPrincipal().signum() < 0) {
            findings.add(finding(loanId, "MIGRATED_LOAN_OUTSTANDING_NEGATIVE", "Outstanding principal must be >= 0",
                    "≥ 0", loan.getOutstandingPrincipal()));
        } else if (loan.getSanctionedAmount() != null && loan.getOutstandingPrincipal().compareTo(loan.getSanctionedAmount()) > 0) {
            findings.add(finding(loanId, "MIGRATED_LOAN_OUTSTANDING_EXCEEDS_SANCTIONED",
                    "Outstanding principal exceeds the loan's own sanctioned amount - the DBA's opening position must be the TRUE original "
                            + "sanctioned amount, not a figure smaller than the current outstanding",
                    "≤ " + loan.getSanctionedAmount(), loan.getOutstandingPrincipal()));
        }

        if (loan.getAnnualInterestRate() == null || loan.getAnnualInterestRate().signum() <= 0) {
            findings.add(finding(loanId, "MIGRATED_LOAN_INTEREST_RATE", "Annual interest rate must be present and positive",
                    "> 0", loan.getAnnualInterestRate()));
        }

        List<JciEccsLoanSchedule> schedule = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loanId);
        validateSchedule(loan, schedule, findings);

        return findings;
    }

    private void validateSchedule(JciEccsLoan loan, List<JciEccsLoanSchedule> schedule, List<IntegrityCheckResult> findings) {
        if (loan.getStatus() != JciEccsLoanStatus.ACTIVE || loan.getOutstandingPrincipal() == null
                || loan.getOutstandingPrincipal().signum() <= 0) {
            return; // nothing further to validate for a loan that isn't expected to have future dues
        }

        List<JciEccsLoanSchedule> usable = schedule.stream()
                .filter(s -> s.getStatus() == JciEccsScheduleStatus.FUTURE || s.getStatus() == JciEccsScheduleStatus.DUE
                        || s.getStatus() == JciEccsScheduleStatus.PARTIAL || s.getStatus() == JciEccsScheduleStatus.OVERDUE)
                .toList();
        if (usable.isEmpty()) {
            findings.add(finding(loan.getId(), "MIGRATED_LOAN_NO_FUTURE_SCHEDULE",
                    "Loan has outstanding principal but no FUTURE/DUE/PARTIAL/OVERDUE schedule row - it cannot receive its next payroll demand. "
                            + "The DBA must insert the loan's remaining future installment rows (never historical ones) alongside the loan itself",
                    "≥ 1 usable schedule row", 0));
            return;
        }

        BigDecimal remainingPrincipalDue = usable.stream()
                .map(s -> BigDecimal.valueOf(s.getPrincipalDue()).subtract(s.getPrincipalPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (remainingPrincipalDue.compareTo(loan.getOutstandingPrincipal()) != 0) {
            findings.add(finding(loan.getId(), "MIGRATED_LOAN_SCHEDULE_PRINCIPAL_MISMATCH",
                    "Sum of remaining schedule principal due does not equal the loan's own outstanding principal - the DBA's inserted "
                            + "schedule rows must account for exactly the migrated opening balance",
                    loan.getOutstandingPrincipal(), remainingPrincipalDue));
        }

        // next payroll period resolvable: CycleResolverService always get-or-creates a cycle for any date,
        // so this can only ever fail on a malformed target (it never throws for "no cycle exists yet") -
        // the check worth making is that every usable schedule row actually references a resolvable cycle.
        for (JciEccsLoanSchedule row : usable) {
            if (row.getCycle() == null) {
                findings.add(finding(loan.getId(), "MIGRATED_LOAN_SCHEDULE_CYCLE_MISSING",
                        "Schedule installment #" + row.getInstallmentNo() + " has no payroll cycle reference", "non-null", "null"));
            }
        }
        // Confirms the resolver itself is reachable for a fresh target month - a defensive check, not a
        // per-row one, since every JCIECCS date-to-cycle resolution funnels through this one method.
        cycleResolverService.resolveForDate(LocalDate.now());
    }

    private IntegrityCheckResult finding(Long loanId, String checkType, String message, Object expected, Object actual) {
        return IntegrityCheckResult.of(checkType, "JCIECCS_LOAN", loanId, Severity.CRITICAL, message, expected, actual);
    }
}
