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
     * the LOAN_WITHDRAWAL's totalDebit and is reduced by each PRINCIPAL-phase LOAN_REPAYMENT's totalCredit
     * (identified by eeShareCredit+vpfCredit > 0 - see CpfLedgerSyncService.postLoanInterestRecoveryEntry's
     * own comment for why an INTEREST-phase LOAN_REPAYMENT row must NOT be mistaken for one of these).
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
                    && entry.getEeShareCredit().add(entry.getVpfCredit()).signum() > 0) {
                outstanding = outstanding.subtract(entry.getTotalCredit()).max(BigDecimal.ZERO);
            }
        }
        return outstanding;
    }

    @Transactional
    public CpfLoanSettlementResponse processCashSettlement(Long loanId, CpfLoanSettlementRequest request, Long receivedByOfficerId) {
        CpfLoanApplication loan = findOrThrow(loanId);
        if (loan.getStatus() != CpfLoanApplicationStatus.DISBURSED) {
            throw new BusinessRuleViolationException(
                    "CPF Loan Application " + loanId + " must be DISBURSED to record a cash settlement but is " + loan.getStatus());
        }

        CpfLoanSettlementQuoteResponse quote = buildQuote(loan, LocalDate.now());

        BigDecimal priorOutstandingBalance = loan.getOutstandingBalance();
        BigDecimal newOutstandingBalance = priorOutstandingBalance.subtract(request.principalPaid()).max(BigDecimal.ZERO);
        boolean earlyForeclosure = priorOutstandingBalance.signum() > 0 && newOutstandingBalance.signum() == 0;

        BigDecimal interestRebateApplied = BigDecimal.ZERO;
        BigDecimal newOutstandingInterest = loan.getOutstandingInterest().subtract(request.interestPaid());
        if (earlyForeclosure) {
            interestRebateApplied = quote.interestRebateAmount();
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

        if (request.principalPaid().signum() > 0) {
            postPrincipalSettlementLedgerEntry(loan, request.principalPaid());
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
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }

        LocalDate valueDate = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        BigDecimal newEe = priorEe.add(principalPaid);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_REPAYMENT,
                newEe, priorEr, priorVpf, newEe.add(priorEr).add(priorVpf));
        entry.setEeShareCredit(principalPaid);
        entry.setTotalCredit(principalPaid);
        entry.setLoan(loan);
        entry.setRemarks("Direct cash settlement principal repayment against " + loan.getLoanApplicationNo());
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
