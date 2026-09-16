package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanSettlementQuoteResponse;
import in.gov.jci.hrms.dto.CpfLoanSettlementRequest;
import in.gov.jci.hrms.dto.CpfLoanSettlementResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanSettlementTransaction;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfLoanSettlementRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Direct, out-of-payroll cash/instrument settlement of a DISBURSED CPF Trust loan - lets a member (or a
 * separating employee's terminal settlement) close a loan today rather than waiting out the remaining
 * payroll recovery installments, with a statutory interest rebate for whatever tenure they didn't actually
 * use. Both operations here work from the loan's own ledger history (cpf_trust_member_ledger_entries rows
 * with loan_id = this loan), not a synthetic amortization schedule, so the recomputed interest reflects
 * what the member's outstanding principal actually was month to month, not what the original sanction
 * schedule assumed it would be.
 */
@Service
@Transactional(readOnly = true)
public class CpfLoanSettlementService {

    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private final CpfLoanApplicationRepository loanRepository;
    private final CpfLoanSettlementRepository settlementRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final EmployeeRepository employeeRepository;

    public CpfLoanSettlementService(CpfLoanApplicationRepository loanRepository, CpfLoanSettlementRepository settlementRepository,
                                     CpfTrustMemberLedgerEntryRepository ledgerRepository, EmployeeRepository employeeRepository) {
        this.loanRepository = loanRepository;
        this.settlementRepository = settlementRepository;
        this.ledgerRepository = ledgerRepository;
        this.employeeRepository = employeeRepository;
    }

    public CpfLoanSettlementQuoteResponse calculateEarlySettlementQuote(Long loanId) {
        CpfLoanApplication loan = findOrThrow(loanId);
        if (loan.getStatus() != CpfLoanApplicationStatus.DISBURSED) {
            throw new BusinessRuleViolationException(
                    "CPF Loan Application " + loanId + " must be DISBURSED to quote an early settlement but is " + loan.getStatus());
        }
        return buildQuote(loan, LocalDate.now());
    }

    private CpfLoanSettlementQuoteResponse buildQuote(CpfLoanApplication loan, LocalDate asOfDate) {
        LocalDate disbursedDate = LocalDate.ofInstant(loan.getDisbursedAt(), java.time.ZoneId.systemDefault());
        List<LocalDate> checkpoints = elapsedMonthCheckpoints(disbursedDate, asOfDate);
        List<CpfTrustMemberLedgerEntry> loanEntries = ledgerRepository.findByLoan_IdOrderByValueDateAscIdAsc(loan.getId());

        BigDecimal sumOfElapsedBalances = BigDecimal.ZERO;
        for (LocalDate checkpoint : checkpoints) {
            sumOfElapsedBalances = sumOfElapsedBalances.add(outstandingPrincipalAsOf(loanEntries, checkpoint));
        }

        BigDecimal recomputedStatutoryInterest = sumOfElapsedBalances.multiply(loan.getInterestRate())
                .divide(TWELVE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal originalProjectedInterest = loan.getTotalInterestAmount();
        BigDecimal interestRebateAmount = originalProjectedInterest.subtract(recomputedStatutoryInterest).max(BigDecimal.ZERO);
        BigDecimal netPayoffAmount = loan.getOutstandingBalance()
                .add(loan.getOutstandingInterest().subtract(interestRebateAmount))
                .max(BigDecimal.ZERO);

        return new CpfLoanSettlementQuoteResponse(loan.getId(), checkpoints.size(), loan.getOutstandingBalance(), loan.getOutstandingInterest(),
                originalProjectedInterest, recomputedStatutoryInterest, interestRebateAmount, netPayoffAmount);
    }

    /** One checkpoint per elapsed month since disbursement - every completed month's own month-end, plus asOfDate itself for the current, still-running month. */
    private List<LocalDate> elapsedMonthCheckpoints(LocalDate disbursedDate, LocalDate asOfDate) {
        List<LocalDate> checkpoints = new ArrayList<>();
        YearMonth cursor = YearMonth.from(disbursedDate);
        YearMonth currentMonth = YearMonth.from(asOfDate);
        while (cursor.isBefore(currentMonth)) {
            checkpoints.add(cursor.atEndOfMonth());
            cursor = cursor.plusMonths(1);
        }
        checkpoints.add(asOfDate);
        return checkpoints;
    }

    /**
     * Walks this loan's own ledger rows to find the outstanding principal as of a given date - starts at
     * the LOAN_WITHDRAWAL's totalDebit and is reduced by each PRINCIPAL-phase LOAN_REPAYMENT's
     * loanRepayPrincipal. Discriminated via loanRepayPrincipal (not eeShareCredit+vpfCredit) since an
     * INTEREST-phase LOAN_REPAYMENT row now also carries an EE credit (loanRepayInterest) - see
     * CpfLedgerSyncService.postLoanInterestRecoveryEntry()'s own comment.
     */
    private BigDecimal outstandingPrincipalAsOf(List<CpfTrustMemberLedgerEntry> loanEntries, LocalDate asOf) {
        BigDecimal outstanding = BigDecimal.ZERO;
        for (CpfTrustMemberLedgerEntry entry : loanEntries) {
            if (entry.getValueDate().isAfter(asOf)) {
                break;
            }
            if (entry.getEntryType() == CpfLedgerEntryType.LOAN_WITHDRAWAL) {
                outstanding = entry.getTotalDebit();
            } else if (entry.getEntryType() == CpfLedgerEntryType.LOAN_REPAYMENT
                    && entry.getLoanRepayPrincipal().signum() > 0) {
                outstanding = outstanding.subtract(entry.getLoanRepayPrincipal()).max(BigDecimal.ZERO);
            }
        }
        return outstanding;
    }

    /**
     * Part 21/23 - locks the loan row before reading/mutating its outstanding balance (serializing
     * against a concurrent payroll recovery posting on the same loan - see
     * CpfLedgerSyncService.applyTwoPhaseLoanRecovery()'s own matching lock), and is idempotent on
     * (loanId, instrumentOrChallanNo): a retried request carrying the same instrument/challan number for
     * the same loan returns the original settlement instead of posting a second one.
     */
    @Transactional
    public CpfLoanSettlementResponse processCashSettlement(Long loanId, CpfLoanSettlementRequest request, Long receivedByOfficerId) {
        // Lock the loan row FIRST (Part 21) - a second, truly-concurrent request for the same
        // (loanId, instrumentOrChallanNo) blocks here until the first commits, so the idempotency check
        // right after is guaranteed to see that first request's already-committed settlement row rather
        // than racing it (the uq_cpf_loan_settlement_loan_instrument constraint, V84, is the final
        // backstop if this lock is ever bypassed).
        CpfLoanApplication loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new MasterDataNotFoundException("CPF Loan Application", loanId));

        var existing = settlementRepository.findByLoanIdAndInstrumentOrChallanNo(loanId, request.instrumentOrChallanNo());
        if (existing.isPresent()) {
            return CpfLoanSettlementResponse.from(existing.get());
        }

        if (loan.getStatus() != CpfLoanApplicationStatus.DISBURSED) {
            throw new BusinessRuleViolationException(
                    "CPF Loan Application " + loanId + " must be DISBURSED to record a cash settlement but is " + loan.getStatus());
        }
        // SEC-003 maker != checker (docs/security/MAKER_CHECKER_IMPLEMENTATION.md), extended to
        // settle-cash per the SEC-010 closure review: settlement is a financially authoritative
        // transition (it reduces outstanding balance, credits the member's own EE ledger, and can close
        // the loan) exactly like sanction/disburse, so the officer who applied for the loan may not also
        // be the one who receives its cash settlement, even if they hold a role that would otherwise
        // authorize it. Historical loans with no recorded applicantEmployeeId are not retroactively
        // blocked - checked, and rejected, before any balance/ledger mutation below.
        Long applicantId = loan.getApplicantEmployeeId();
        if (applicantId != null && applicantId.equals(receivedByOfficerId)) {
            throw new AccessDeniedException(
                    "CPF Loan Application " + loanId + ": settle-cash cannot be performed by the same employee who applied for it");
        }

        CpfLoanSettlementQuoteResponse quote = buildQuote(loan, LocalDate.now());

        BigDecimal priorOutstandingBalance = loan.getOutstandingBalance();
        // Part 22/55 invariant ("recovered principal <= outstanding principal"): cap what actually gets
        // applied/credited at what was still genuinely outstanding under this now-locked, live read - a
        // request.principalPaid() larger than that (an overpayment, or a stale figure computed before a
        // concurrent payroll recovery already reduced this same balance) must never over-credit the
        // member's own EE ledger balance. The transaction record itself (txn.setPrincipalPaid below)
        // still stores what the member actually remitted, for reconciliation.
        BigDecimal principalApplied = request.principalPaid().min(priorOutstandingBalance).max(BigDecimal.ZERO);
        BigDecimal newOutstandingBalance = priorOutstandingBalance.subtract(principalApplied).max(BigDecimal.ZERO);
        boolean earlyForeclosure = priorOutstandingBalance.signum() > 0 && newOutstandingBalance.signum() == 0;

        // Same overpayment guard as principal above: the member's actual interest PAYMENT is capped at
        // what was genuinely still outstanding before any rebate is considered - a rebate is a waiver
        // (Trust income foregone), not cash received, so it must never itself generate an EE credit.
        BigDecimal priorOutstandingInterest = loan.getOutstandingInterest();
        BigDecimal interestPaymentApplied = request.interestPaid().min(priorOutstandingInterest).max(BigDecimal.ZERO);
        BigDecimal newOutstandingInterest = priorOutstandingInterest.subtract(interestPaymentApplied);
        BigDecimal interestRebateApplied = BigDecimal.ZERO;
        if (earlyForeclosure) {
            interestRebateApplied = quote.interestRebateAmount().min(newOutstandingInterest).max(BigDecimal.ZERO);
            newOutstandingInterest = newOutstandingInterest.subtract(interestRebateApplied);
        }
        newOutstandingInterest = newOutstandingInterest.max(BigDecimal.ZERO);

        loan.setOutstandingBalance(newOutstandingBalance);
        loan.setOutstandingInterest(newOutstandingInterest);
        if (newOutstandingBalance.signum() == 0 && newOutstandingInterest.signum() == 0) {
            loan.setRecoveryPhase(CpfLoanRecoveryPhase.CLOSED);
            loan.setStatus(CpfLoanApplicationStatus.CLOSED);
            loan.setPreclosed(true);
            loan.setPreclosedAt(Instant.now());
        } else if (newOutstandingBalance.signum() == 0) {
            loan.setRecoveryPhase(CpfLoanRecoveryPhase.INTEREST);
        }

        if (principalApplied.signum() > 0) {
            postPrincipalSettlementLedgerEntry(loan, principalApplied);
        }
        // Business decision (confirmed): cash-settled interest is credited to the member's own EE share,
        // exactly like payroll-recovered interest (CpfLedgerSyncService.postLoanInterestRecoveryEntry) -
        // the rebate itself is never credited, only the interest actually paid in cash.
        if (interestPaymentApplied.signum() > 0) {
            postInterestSettlementLedgerEntry(loan, interestPaymentApplied);
        }

        LocalDate today = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(today);
        CpfLoanSettlementTransaction txn = new CpfLoanSettlementTransaction(generateReceiptVoucherNo(), loan, loan.getEmployee(), finYear,
                request.settlementType(), request.instrumentOrChallanNo(), request.instrumentDate(), request.bankRealizationDate(),
                request.trustBankAccountCode(), request.principalPaid().add(request.interestPaid()));
        txn.setPrincipalPaid(request.principalPaid());
        txn.setInterestPaid(request.interestPaid());
        txn.setEarlyForeclosure(earlyForeclosure);
        txn.setElapsedMonths(quote.elapsedMonths());
        txn.setOriginalProjectedInterest(quote.originalProjectedInterest());
        txn.setRecomputedStatutoryInterest(quote.recomputedStatutoryInterest());
        txn.setInterestRebateAmount(interestRebateApplied);
        txn.setChallanDocRef(request.challanDocRef());
        txn.setRemarks(request.remarks());
        if (receivedByOfficerId != null) {
            employeeRepository.findById(receivedByOfficerId).ifPresent(txn::setReceivedBy);
        }

        return CpfLoanSettlementResponse.from(settlementRepository.saveAndFlush(txn));
    }

    /** Restores the member's own EE ledger balance by the principal paid - same convention as the payroll-recovery PRINCIPAL phase (CpfLedgerSyncService), so both paths credit EE consistently regardless of how the recovery happened. */
    private void postPrincipalSettlementLedgerEntry(CpfLoanApplication loan, BigDecimal principalPaid) {
        Employee employee = loan.getEmployee();
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

        LocalDate valueDate = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        BigDecimal newEe = priorEe.add(principalPaid);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT,
                newEe, priorEr, priorVpf, newEe.add(priorEr).add(priorVpf));
        entry.setEeShareCredit(principalPaid);
        entry.setTotalCredit(principalPaid);
        entry.setLoanRepayPrincipal(principalPaid);
        entry.setRunningLoanCpfBalance(priorLoanCpfBalance.subtract(principalPaid));
        entry.setRunningNrwEeBalance(priorNrwEeBalance);
        entry.setRunningNrwErBalance(priorNrwErBalance);
        entry.setRunningNrwVpfBalance(priorNrwVpfBalance);
        entry.setLoan(loan);
        entry.setRemarks("Direct cash settlement principal repayment against " + loan.getLoanApplicationNo());
        ledgerRepository.save(entry);
    }

    /**
     * Credits the member's own EE share by the interest actually paid in cash - same accounting rule as
     * CpfLedgerSyncService.postLoanInterestRecoveryEntry()'s own payroll-recovery interest phase (interest
     * recovered on a CPF loan remains Trust income for statutory-filing purposes, but is folded back into
     * the member's own EE corpus), so a member paying off interest by cash settlement is treated
     * identically to one whose interest was recovered through payroll. runningLoanCpfBalance carries
     * forward unchanged - interest was never part of the tracked principal balance.
     */
    private void postInterestSettlementLedgerEntry(CpfLoanApplication loan, BigDecimal interestPaid) {
        Employee employee = loan.getEmployee();
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

        LocalDate valueDate = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        BigDecimal newEe = priorEe.add(interestPaid);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT,
                newEe, priorEr, priorVpf, newEe.add(priorEr).add(priorVpf));
        entry.setEeShareCredit(interestPaid);
        entry.setInterestCredit(interestPaid);
        entry.setLoanRepayInterest(interestPaid);
        entry.setTotalCredit(interestPaid);
        entry.setRunningLoanCpfBalance(priorLoanCpfBalance);
        entry.setRunningNrwEeBalance(priorNrwEeBalance);
        entry.setRunningNrwErBalance(priorNrwErBalance);
        entry.setRunningNrwVpfBalance(priorNrwVpfBalance);
        entry.setLoan(loan);
        entry.setRemarks("Direct cash settlement interest repayment against " + loan.getLoanApplicationNo());
        ledgerRepository.save(entry);
    }

    private CpfLoanApplication findOrThrow(Long id) {
        return loanRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("CPF Loan Application", id));
    }

    /** "CPFL-RCPT/{finYear}/{seq, 4 digits}" - same low-volume, human-paced-workflow rationale as IncomingFundTransferService's own voucher generator for why this isn't advisory-lock-guarded. */
    private String generateReceiptVoucherNo() {
        String finYear = IncomingFundTransferService.financialYearFor(LocalDate.now());
        String prefix = "CPFL-RCPT/" + finYear + "/";
        long seq = settlementRepository.countByReceiptVoucherNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }
}
