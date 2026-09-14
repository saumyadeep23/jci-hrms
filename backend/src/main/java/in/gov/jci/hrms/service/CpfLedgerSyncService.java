package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanBatchRecovery;
import in.gov.jci.hrms.entity.CpfLoanBatchRecoveryOutcome;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.PayrollRun;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfLoanBatchRecoveryRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Post-payroll CPF Trust ledger sync - fires once a PayrollBatch is DISBURSED (see disburse()). The
 * original task spec described this as a hook on an existing "batch reaches DISBURSED or LOCKED" event,
 * but neither transition actually existed anywhere in this codebase yet (PayrollBatchComputationService
 * only ever reaches HR_FINALIZED - see its own finalizeBatch()) and PayrollBatchStatus has no LOCKED value
 * at all. disburse() below is the actual (new) trigger point this service needed to have any real caller;
 * see PayrollBatchController's new /disburse endpoint.
 *
 * cpf_trust_member_ledger_entries.payroll_run_id references the legacy payroll_runs table (matching that
 * live table's own pre-existing shape - see V74's header comment), even though the CPF/VPF/JCPF amounts
 * synced here are read from the newer PayrollBatch-linked payroll_monthly_head_items/
 * payroll_monthly_statutory_items - the only place this codebase actually computes and stores them. The
 * link is populated on a best-effort basis (a same-month/year PayrollRun, if one happens to exist) rather
 * than left to drive the computation itself.
 *
 * Loan recovery targets cpf_loan_applications (the CPF Trust's own loan/withdrawal table - also
 * pre-existing live, see CpfLoanApplication's own javadoc), not employee_loans: Head 30/51 already gives
 * a clean, payroll-computed PRINCIPAL-only recovery figure, so this reduces outstandingBalance and
 * increments recoveredInstallments directly rather than re-deriving a split through a different formula.
 *
 * Two-phase recovery: a DISBURSED loan's recoveryPhase gates which head this method acts on for that
 * employee this batch - PRINCIPAL reads Head 30/51 and restores the member's own EE ledger balance (the
 * money being repaid was theirs) and reduces runningLoanCpfBalance; once outstandingBalance reaches zero
 * the loan flips to INTEREST, which reads Head 31, reduces outstandingInterest, and - per the Member CPF
 * Passbook's accounting rule - ALSO credits the member's EE running balance (interest recovered on a CPF
 * loan remains Trust income for statutory-filing purposes, but is folded back into the member's own EE
 * corpus here). Both phases still post an entry_type = LOAN_REPAYMENT row (not a separate
 * LOAN_INTEREST_PAYMENT value): the live cpf_trust_member_ledger_entries.entry_type CHECK constraint
 * doesn't include one, and this task deliberately does not add a migration for it - see
 * postLoanInterestRecoveryEntry()'s own comment for how the two phases are now told apart on read (via
 * loanRepayPrincipal/loanRepayInterest, not eeShareCredit, since both now carry an EE credit).
 */
@Service
@Transactional(readOnly = true)
public class CpfLedgerSyncService {

    private static final int HEAD_CPF = 27;
    private static final int HEAD_VPF = 28;
    private static final int HEAD_ARR_CPF = 29;
    private static final int HEAD_CPFLOAN_PRIN = 30;
    private static final int HEAD_CPFLOAN_INT = 31;
    private static final int HEAD_NREFLOAN_PRIN = 51;
    private static final int STAT_HEAD_JCPF = 3;
    private static final int STAT_HEAD_ARR_JCPF = 12;

    private static final List<CpfLoanApplicationStatus> RECOVERABLE_LOAN_STATUSES =
            List.of(CpfLoanApplicationStatus.DISBURSED, CpfLoanApplicationStatus.SANCTIONED);

    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    private final PayrollMonthlyHeadItemRepository headItemRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfLoanApplicationRepository cpfLoanApplicationRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final CpfLoanBatchRecoveryRepository batchRecoveryRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public CpfLedgerSyncService(PayrollBatchRepository payrollBatchRepository, PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                 PayrollMonthlyHeadItemRepository headItemRepository, PayrollMonthlyStatutoryItemRepository statutoryItemRepository,
                                 CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfLoanApplicationRepository cpfLoanApplicationRepository,
                                 PayrollRunRepository payrollRunRepository, CpfLoanBatchRecoveryRepository batchRecoveryRepository) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
        this.ledgerRepository = ledgerRepository;
        this.batchRecoveryRepository = batchRecoveryRepository;
        this.cpfLoanApplicationRepository = cpfLoanApplicationRepository;
        this.payrollRunRepository = payrollRunRepository;
    }

    /** HR_FINALIZED -> DISBURSED (FINANCE_APPROVED also accepted, for whenever that transition itself gets built) - then syncs the CPF Trust ledger for every employee in the batch. */
    @Transactional
    public PayrollBatch disburse(Long batchId, Long disbursedByEmployeeId) {
        PayrollBatch batch = payrollBatchRepository.findById(batchId)
                .orElseThrow(() -> new MasterDataNotFoundException("Payroll Batch", batchId));
        if (batch.getStatus() != PayrollBatchStatus.HR_FINALIZED && batch.getStatus() != PayrollBatchStatus.FINANCE_APPROVED) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batchId + " must be HR_FINALIZED or FINANCE_APPROVED to disburse but is " + batch.getStatus());
        }
        batch.setStatus(PayrollBatchStatus.DISBURSED);
        syncForBatch(batch);
        return batch;
    }

    /** Callable directly against an already-DISBURSED batch (e.g. by a test, or a re-run) without going through disburse() again. */
    @Transactional
    public void syncForBatch(PayrollBatch batch) {
        if (batch.getStatus() != PayrollBatchStatus.DISBURSED) {
            throw new BusinessRuleViolationException(
                    "Payroll batch " + batch.getId() + " must be DISBURSED to sync its CPF ledger but is " + batch.getStatus());
        }
        LocalDate valueDate = YearMonth.of(batch.getSalYear(), batch.getSalMonth()).atEndOfMonth();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        var payrollRun = payrollRunRepository.findByCycleYearAndCycleMonth(batch.getSalYear(), batch.getSalMonth()).orElse(null);

        for (PayrollMonthlyRecord record : payrollMonthlyRecordRepository.findByBatch_Id(batch.getId())) {
            Employee employee = record.getEmployee();
            List<PayrollMonthlyHeadItem> headItems = headItemRepository.findByRecord_TranId(record.getTranId());
            List<PayrollMonthlyStatutoryItem> statItems = statutoryItemRepository.findByRecord_TranId(record.getTranId());

            BigDecimal eeCredit = sumHeads(headItems, HEAD_CPF, HEAD_ARR_CPF);
            BigDecimal vpfCredit = sumHeads(headItems, HEAD_VPF);
            BigDecimal erCredit = sumStats(statItems, STAT_HEAD_JCPF, STAT_HEAD_ARR_JCPF);
            BigDecimal principalRecoveryHead = sumHeads(headItems, HEAD_CPFLOAN_PRIN, HEAD_NREFLOAN_PRIN);
            BigDecimal interestRecoveryHead = sumHeads(headItems, HEAD_CPFLOAN_INT);

            if (eeCredit.signum() > 0 || vpfCredit.signum() > 0 || erCredit.signum() > 0) {
                postLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.PAYROLL_MONTHLY, payrollRun, record,
                        eeCredit, erCredit, vpfCredit, BigDecimal.ZERO, null,
                        "Payroll " + batch.getSalMonth() + "/" + batch.getSalYear() + " CPF/VPF/JCPF credit");
            }

            if (principalRecoveryHead.signum() > 0 || interestRecoveryHead.signum() > 0) {
                applyTwoPhaseLoanRecovery(employee, finYear, valueDate, payrollRun, record, batch, principalRecoveryHead, interestRecoveryHead);
            }
        }
    }

    /**
     * Acts on whichever head matches the employee's active loan's CURRENT recoveryPhase only - a payroll
     * cycle should never populate both Head 30/51 and Head 31 for the same employee in the same month (the
     * payroll computation engine itself is phase-aware), so this doesn't attempt to reconcile both heads
     * firing at once.
     *
     * <h2>Idempotency (Part 24)</h2>
     * This class's own javadoc documents {@link #syncForBatch} as re-runnable against an already-DISBURSED
     * batch. Before touching anything, this method checks {@code cpf_loan_batch_recovery} for an existing
     * (loan, batch, phase) row - if one exists, this employee's recovery for this exact batch has already
     * been resolved and posting again is skipped entirely.
     *
     * <h2>Concurrency (Part 21/22)</h2>
     * The candidate loan is re-fetched via {@link CpfLoanApplicationRepository#findByIdForUpdate} - a real
     * "SELECT ... FOR UPDATE" - immediately before reading/mutating its outstanding balance, so a
     * concurrent {@link CpfLoanSettlementService#processCashSettlement} against the same loan serializes
     * against this posting rather than both acting on the same stale outstanding figure.
     *
     * <h2>Live-balance recheck (Part 17/26)</h2>
     * If the lock reveals the loan already CLOSED (e.g. a cash settlement landed first), the amount
     * payroll already deducted this month is never silently dropped nor posted against a closed loan - it
     * is recorded as an EXCESS_LOAN_ALREADY_CLOSED row in cpf_loan_batch_recovery for CPF Trust staff to
     * trace and refund/adjust (Part 43), and no ledger entry is posted.
     */
    private void applyTwoPhaseLoanRecovery(Employee employee, String finYear, LocalDate valueDate, PayrollRun payrollRun,
                                            PayrollMonthlyRecord record, PayrollBatch batch, BigDecimal principalRecoveryHead,
                                            BigDecimal interestRecoveryHead) {
        List<CpfLoanApplication> allLoans = cpfLoanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId());
        List<CpfLoanApplication> activeLoans = allLoans.stream()
                .filter(loan -> RECOVERABLE_LOAN_STATUSES.contains(loan.getStatus()))
                .toList();
        if (activeLoans.isEmpty()) {
            // Part 17/26: the loan this payroll-computed amount was meant for may simply no longer be
            // "active" (e.g. it was CLOSED by a cash settlement between payroll computation and this
            // posting - the Employee-108 scenario) rather than genuinely missing. Route to the same
            // excess/audit trail as a mid-lock closure detection below, keyed off the most recent CLOSED
            // loan on file, instead of falling through to the true "no matching loan at all" case, which
            // would otherwise post an orphaned, unexplained ledger entry for money that already has a
            // perfectly good explanation (the loan it belonged to closed first).
            CpfLoanApplication mostRecentClosed = allLoans.stream()
                    .filter(loan -> loan.getStatus() == CpfLoanApplicationStatus.CLOSED)
                    .findFirst().orElse(null);
            BigDecimal alreadyDeducted = principalRecoveryHead.signum() > 0 ? principalRecoveryHead : interestRecoveryHead;
            if (mostRecentClosed != null && alreadyDeducted.signum() > 0) {
                CpfLoanRecoveryPhase phase = principalRecoveryHead.signum() > 0 ? CpfLoanRecoveryPhase.PRINCIPAL : CpfLoanRecoveryPhase.INTEREST;
                if (batchRecoveryRepository.findByLoan_IdAndPayrollBatch_IdAndRecoveryPhase(mostRecentClosed.getId(), batch.getId(), phase).isEmpty()) {
                    batchRecoveryRepository.save(new CpfLoanBatchRecovery(mostRecentClosed, batch, phase, alreadyDeducted,
                            CpfLoanBatchRecoveryOutcome.EXCESS_LOAN_ALREADY_CLOSED, null));
                }
                return;
            }
            // Payroll deducted a loan-recovery head but there's no matching CPF Trust loan on file at
            // all - the ledger entry is still posted (with no loan_id), rather than silently dropped or
            // failing the whole batch sync over one employee's data mismatch. loanRepayPrincipal is
            // stamped for display, but with loan=null there is no tracked runningLoanCpfBalance to reduce.
            postLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT, payrollRun, record,
                    principalRecoveryHead, BigDecimal.ZERO, BigDecimal.ZERO, principalRecoveryHead, null,
                    "Payroll " + batch.getSalMonth() + "/" + batch.getSalYear() + " CPF loan recovery (no matching active loan on file)");
            return;
        }
        CpfLoanApplication candidate = activeLoans.get(0);
        CpfLoanRecoveryPhase candidatePhase = candidate.getRecoveryPhase();
        if (candidatePhase == CpfLoanRecoveryPhase.CLOSED) {
            return;
        }
        if (batchRecoveryRepository.findByLoan_IdAndPayrollBatch_IdAndRecoveryPhase(candidate.getId(), batch.getId(), candidatePhase).isPresent()) {
            return; // already resolved for this exact (loan, batch, phase) - re-run is a safe no-op
        }

        // Detach the already-loaded (unlocked, possibly now-stale) candidate before the locked re-fetch -
        // otherwise Hibernate would resolve findByIdForUpdate() against this same identity-mapped Java
        // object and correctly BLOCK on the DB-level lock, but then hand back the SAME instance with its
        // OLD field values rather than the fresh row a concurrent settlement may have just committed
        // (Part 17/22's whole point). This is why the "no matching active loan" `activeLoans` lookup above
        // must never be trusted for the actual mutation - only `loan`, read fresh here, may be.
        entityManager.detach(candidate);
        CpfLoanApplication loan = cpfLoanApplicationRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
        String periodLabel = "Payroll " + batch.getSalMonth() + "/" + batch.getSalYear();

        if (loan.getStatus() == CpfLoanApplicationStatus.CLOSED || loan.getRecoveryPhase() == CpfLoanRecoveryPhase.CLOSED) {
            BigDecimal alreadyDeducted = principalRecoveryHead.signum() > 0 ? principalRecoveryHead : interestRecoveryHead;
            if (alreadyDeducted.signum() > 0) {
                batchRecoveryRepository.save(new CpfLoanBatchRecovery(loan, batch, candidatePhase, alreadyDeducted,
                        CpfLoanBatchRecoveryOutcome.EXCESS_LOAN_ALREADY_CLOSED, null));
            }
            return;
        }

        if (loan.getRecoveryPhase() == CpfLoanRecoveryPhase.PRINCIPAL && principalRecoveryHead.signum() > 0
                && loan.getOutstandingBalance().signum() <= 0) {
            batchRecoveryRepository.save(new CpfLoanBatchRecovery(loan, batch, CpfLoanRecoveryPhase.PRINCIPAL, principalRecoveryHead,
                    CpfLoanBatchRecoveryOutcome.EXCESS_NO_OUTSTANDING, null));
        } else if (loan.getRecoveryPhase() == CpfLoanRecoveryPhase.INTEREST && interestRecoveryHead.signum() > 0
                && loan.getOutstandingInterest().signum() <= 0) {
            batchRecoveryRepository.save(new CpfLoanBatchRecovery(loan, batch, CpfLoanRecoveryPhase.INTEREST, interestRecoveryHead,
                    CpfLoanBatchRecoveryOutcome.EXCESS_NO_OUTSTANDING, null));
        } else if (loan.getRecoveryPhase() == CpfLoanRecoveryPhase.PRINCIPAL && principalRecoveryHead.signum() > 0) {
            BigDecimal principalComponent = principalRecoveryHead.min(loan.getOutstandingBalance());
            loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(principalComponent));
            loan.setRecoveredInstallments(loan.getRecoveredInstallments() + 1);
            CpfTrustMemberLedgerEntry entry = postLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT, payrollRun, record,
                    principalComponent, BigDecimal.ZERO, BigDecimal.ZERO, principalComponent, loan,
                    periodLabel + " CPF loan principal recovery (Head 30/51)");
            batchRecoveryRepository.save(new CpfLoanBatchRecovery(loan, batch, CpfLoanRecoveryPhase.PRINCIPAL, principalComponent,
                    CpfLoanBatchRecoveryOutcome.RECOVERED, entry));

            if (loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setOutstandingBalance(BigDecimal.ZERO);
                // A loan with no interest still owed (e.g. outstandingInterest never got populated - no
                // sanctionLoan()-computed schedule) has nothing left for a future Head 31 to ever recover,
                // so it must close right here rather than sit in INTEREST phase forever.
                if (loan.getOutstandingInterest().compareTo(BigDecimal.ZERO) <= 0) {
                    loan.setRecoveryPhase(CpfLoanRecoveryPhase.CLOSED);
                    loan.setStatus(CpfLoanApplicationStatus.CLOSED);
                } else {
                    loan.setRecoveryPhase(CpfLoanRecoveryPhase.INTEREST);
                }
            }
        } else if (loan.getRecoveryPhase() == CpfLoanRecoveryPhase.INTEREST && interestRecoveryHead.signum() > 0) {
            BigDecimal interestComponent = interestRecoveryHead.min(loan.getOutstandingInterest());
            loan.setOutstandingInterest(loan.getOutstandingInterest().subtract(interestComponent));
            loan.setRecoveredInterestInstallments(loan.getRecoveredInterestInstallments() + 1);
            CpfTrustMemberLedgerEntry entry = postLoanInterestRecoveryEntry(employee, finYear, valueDate, payrollRun, record, loan, interestComponent,
                    periodLabel + " CPF loan interest recovery (Head 31)");
            batchRecoveryRepository.save(new CpfLoanBatchRecovery(loan, batch, CpfLoanRecoveryPhase.INTEREST, interestComponent,
                    CpfLoanBatchRecoveryOutcome.RECOVERED, entry));

            if (loan.getOutstandingInterest().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setOutstandingInterest(BigDecimal.ZERO);
                loan.setRecoveryPhase(CpfLoanRecoveryPhase.CLOSED);
                loan.setStatus(CpfLoanApplicationStatus.CLOSED);
            }
        }
    }

    /**
     * Interest-phase recovery entry: entry_type = LOAN_REPAYMENT (see this class's own javadoc for why,
     * not LOAN_INTEREST_PAYMENT) with interestCredit/totalCredit/loanRepayInterest set to the amount
     * recovered, and - per the Member CPF Passbook's accounting rule - credited into the member's own EE
     * share the same as a principal repayment. loanRepayPrincipal/loanRepayInterest (not
     * eeShareCredit+vpfCredit) are what distinguish a PRINCIPAL-phase LOAN_REPAYMENT row from this one now
     * that both credit EE - see CpfLoanSettlementService.outstandingPrincipalAsOf()'s own comment.
     * runningLoanCpfBalance carries forward unchanged - interest was never part of the tracked principal.
     */
    private CpfTrustMemberLedgerEntry postLoanInterestRecoveryEntry(Employee employee, String finYear, LocalDate valueDate, PayrollRun payrollRun,
                                                PayrollMonthlyRecord record, CpfLoanApplication loan, BigDecimal interestRecovered, String remarks) {
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        BigDecimal priorLoanCpfBalance = BigDecimal.ZERO;
        BigDecimal priorNrwEeBalance = BigDecimal.ZERO;
        BigDecimal priorNrwErBalance = BigDecimal.ZERO;
        BigDecimal priorNrwVpfBalance = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
            priorLoanCpfBalance = priorEntry.get().getRunningLoanCpfBalance();
            priorNrwEeBalance = priorEntry.get().getRunningNrwEeBalance();
            priorNrwErBalance = priorEntry.get().getRunningNrwErBalance();
            priorNrwVpfBalance = priorEntry.get().getRunningNrwVpfBalance();
        }
        BigDecimal newEe = priorEe.add(interestRecovered);

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT,
                newEe, priorEr, priorVpf, newEe.add(priorEr).add(priorVpf));
        entry.setSalMonth(record.getMonth());
        entry.setSalYear(record.getYear());
        entry.setEeShareCredit(interestRecovered);
        entry.setInterestCredit(interestRecovered);
        entry.setLoanRepayInterest(interestRecovered);
        entry.setTotalCredit(interestRecovered);
        entry.setRunningLoanCpfBalance(priorLoanCpfBalance);
        entry.setRunningNrwEeBalance(priorNrwEeBalance);
        entry.setRunningNrwErBalance(priorNrwErBalance);
        entry.setRunningNrwVpfBalance(priorNrwVpfBalance);
        entry.setPayrollRun(payrollRun);
        entry.setLoan(loan);
        entry.setRemarks(remarks);
        return ledgerRepository.save(entry);
    }

    private CpfTrustMemberLedgerEntry postLedgerEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                  PayrollRun payrollRun, PayrollMonthlyRecord record, BigDecimal eeCredit,
                                  BigDecimal erCredit, BigDecimal vpfCredit, BigDecimal loanRepayPrincipal,
                                  CpfLoanApplication loan, String remarks) {
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        BigDecimal priorLoanCpfBalance = BigDecimal.ZERO;
        BigDecimal priorNrwEeBalance = BigDecimal.ZERO;
        BigDecimal priorNrwErBalance = BigDecimal.ZERO;
        BigDecimal priorNrwVpfBalance = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
            priorLoanCpfBalance = priorEntry.get().getRunningLoanCpfBalance();
            priorNrwEeBalance = priorEntry.get().getRunningNrwEeBalance();
            priorNrwErBalance = priorEntry.get().getRunningNrwErBalance();
            priorNrwVpfBalance = priorEntry.get().getRunningNrwVpfBalance();
        }
        BigDecimal newEe = priorEe.add(eeCredit);
        BigDecimal newEr = priorEr.add(erCredit);
        BigDecimal newVpf = priorVpf.add(vpfCredit);
        // Only reduce the tracked runningLoanCpfBalance when this repayment is actually attributed to a
        // loan on file - an orphaned recovery (loan == null, see the no-matching-active-loan fallback
        // above) still shows loanRepayPrincipal for display but isn't backed by a tracked principal balance.
        BigDecimal newLoanCpfBalance = loan != null ? priorLoanCpfBalance.subtract(loanRepayPrincipal) : priorLoanCpfBalance;

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, entryType,
                newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setSalMonth(record.getMonth());
        entry.setSalYear(record.getYear());
        entry.setEeShareCredit(eeCredit);
        entry.setErShareCredit(erCredit);
        entry.setVpfCredit(vpfCredit);
        entry.setTotalCredit(eeCredit.add(erCredit).add(vpfCredit));
        entry.setLoanRepayPrincipal(loanRepayPrincipal);
        entry.setRunningLoanCpfBalance(newLoanCpfBalance);
        entry.setRunningNrwEeBalance(priorNrwEeBalance);
        entry.setRunningNrwErBalance(priorNrwErBalance);
        entry.setRunningNrwVpfBalance(priorNrwVpfBalance);
        entry.setPayrollRun(payrollRun);
        entry.setLoan(loan);
        entry.setRemarks(remarks);
        return ledgerRepository.save(entry);
    }

    private BigDecimal sumHeads(List<PayrollMonthlyHeadItem> items, int... headCounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollMonthlyHeadItem item : items) {
            for (int headCount : headCounts) {
                if (item.getHeadCount() == headCount) {
                    total = total.add(item.getAmount());
                }
            }
        }
        return total;
    }

    private BigDecimal sumStats(List<PayrollMonthlyStatutoryItem> items, int... statHeadCounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollMonthlyStatutoryItem item : items) {
            for (int statHeadCount : statHeadCounts) {
                if (item.getStatHeadCount() == statHeadCount) {
                    total = total.add(item.getAmount());
                }
            }
        }
        return total;
    }
}
