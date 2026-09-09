package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
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
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollRunRepository;
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
 * money being repaid was theirs); once outstandingBalance reaches zero the loan flips to INTEREST, which
 * reads Head 31 and reduces outstandingInterest, but does NOT credit the member's EE/ER/VPF running
 * balances - CPF loan interest is Trust income, not a return of the member's own corpus. Both phases still
 * post an entry_type = LOAN_REPAYMENT row (not a separate LOAN_INTEREST_PAYMENT value): the live
 * cpf_trust_member_ledger_entries.entry_type CHECK constraint doesn't include one, and this task
 * deliberately does not add a migration for it - see postLoanInterestRecoveryEntry()'s own comment for how
 * the two are told apart on read (CpfLoanSettlementService's principal-trajectory reconstruction relies on
 * exactly this distinction).
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

    public CpfLedgerSyncService(PayrollBatchRepository payrollBatchRepository, PayrollMonthlyRecordRepository payrollMonthlyRecordRepository,
                                 PayrollMonthlyHeadItemRepository headItemRepository, PayrollMonthlyStatutoryItemRepository statutoryItemRepository,
                                 CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfLoanApplicationRepository cpfLoanApplicationRepository,
                                 PayrollRunRepository payrollRunRepository) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.payrollMonthlyRecordRepository = payrollMonthlyRecordRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
        this.ledgerRepository = ledgerRepository;
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
                        eeCredit, erCredit, vpfCredit, null,
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
     */
    private void applyTwoPhaseLoanRecovery(Employee employee, String finYear, LocalDate valueDate, PayrollRun payrollRun,
                                            PayrollMonthlyRecord record, PayrollBatch batch, BigDecimal principalRecoveryHead,
                                            BigDecimal interestRecoveryHead) {
        List<CpfLoanApplication> activeLoans = cpfLoanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .filter(loan -> RECOVERABLE_LOAN_STATUSES.contains(loan.getStatus()))
                .toList();
        if (activeLoans.isEmpty()) {
            // Payroll deducted a loan-recovery head but there's no matching active CPF Trust loan on file -
            // the ledger entry is still posted (with no loan_id), rather than silently dropped or failing
            // the whole batch sync over one employee's data mismatch.
            postLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT, payrollRun, record,
                    principalRecoveryHead, BigDecimal.ZERO, BigDecimal.ZERO, null,
                    "Payroll " + batch.getSalMonth() + "/" + batch.getSalYear() + " CPF loan recovery (no matching active loan on file)");
            return;
        }
        CpfLoanApplication loan = activeLoans.get(0);
        String periodLabel = "Payroll " + batch.getSalMonth() + "/" + batch.getSalYear();

        if (loan.getRecoveryPhase() == CpfLoanRecoveryPhase.PRINCIPAL && principalRecoveryHead.signum() > 0) {
            BigDecimal principalComponent = principalRecoveryHead.min(loan.getOutstandingBalance());
            loan.setOutstandingBalance(loan.getOutstandingBalance().subtract(principalComponent));
            loan.setRecoveredInstallments(loan.getRecoveredInstallments() + 1);
            postLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT, payrollRun, record,
                    principalComponent, BigDecimal.ZERO, BigDecimal.ZERO, loan, periodLabel + " CPF loan principal recovery (Head 30/51)");

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
            postLoanInterestRecoveryEntry(employee, finYear, valueDate, payrollRun, record, loan, interestComponent,
                    periodLabel + " CPF loan interest recovery (Head 31)");

            if (loan.getOutstandingInterest().compareTo(BigDecimal.ZERO) <= 0) {
                loan.setOutstandingInterest(BigDecimal.ZERO);
                loan.setRecoveryPhase(CpfLoanRecoveryPhase.CLOSED);
                loan.setStatus(CpfLoanApplicationStatus.CLOSED);
            }
        }
    }

    /**
     * Interest-phase recovery entry: entry_type = LOAN_REPAYMENT (see this class's own javadoc for why,
     * not LOAN_INTEREST_PAYMENT) with interestCredit/totalCredit set to the amount recovered for the
     * audit trail, but the running EE/ER/VPF/total balances carried forward UNCHANGED from the member's
     * prior entry - interest recovered on a CPF loan is Trust income, not a credit back to the member's own
     * corpus, so it must never inflate runningTotalBalance the way a PRINCIPAL-phase entry's eeShareCredit
     * does. CpfLoanSettlementService's principal-trajectory reconstruction distinguishes a PRINCIPAL-phase
     * LOAN_REPAYMENT row from this one exactly by that: eeShareCredit+vpfCredit > 0 vs interestCredit > 0
     * with no ee/vpf credit at all.
     */
    private void postLoanInterestRecoveryEntry(Employee employee, String finYear, LocalDate valueDate, PayrollRun payrollRun,
                                                PayrollMonthlyRecord record, CpfLoanApplication loan, BigDecimal interestRecovered, String remarks) {
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT,
                priorEe, priorEr, priorVpf, priorEe.add(priorEr).add(priorVpf));
        entry.setSalMonth(record.getMonth());
        entry.setSalYear(record.getYear());
        entry.setInterestCredit(interestRecovered);
        entry.setTotalCredit(interestRecovered);
        entry.setPayrollRun(payrollRun);
        entry.setLoan(loan);
        entry.setRemarks(remarks);
        ledgerRepository.save(entry);
    }

    private void postLedgerEntry(Employee employee, String finYear, LocalDate valueDate, CpfLedgerEntryType entryType,
                                  PayrollRun payrollRun, PayrollMonthlyRecord record, BigDecimal eeCredit,
                                  BigDecimal erCredit, BigDecimal vpfCredit, CpfLoanApplication loan, String remarks) {
        BigDecimal priorEe = BigDecimal.ZERO;
        BigDecimal priorEr = BigDecimal.ZERO;
        BigDecimal priorVpf = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }
        BigDecimal newEe = priorEe.add(eeCredit);
        BigDecimal newEr = priorEr.add(erCredit);
        BigDecimal newVpf = priorVpf.add(vpfCredit);

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, entryType,
                newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setSalMonth(record.getMonth());
        entry.setSalYear(record.getYear());
        entry.setEeShareCredit(eeCredit);
        entry.setErShareCredit(erCredit);
        entry.setVpfCredit(vpfCredit);
        entry.setTotalCredit(eeCredit.add(erCredit).add(vpfCredit));
        entry.setPayrollRun(payrollRun);
        entry.setLoan(loan);
        entry.setRemarks(remarks);
        ledgerRepository.save(entry);
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
