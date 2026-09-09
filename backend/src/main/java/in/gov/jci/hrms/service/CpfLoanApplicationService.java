package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfLoanApplicationRequest;
import in.gov.jci.hrms.dto.CpfLoanApplicationResponse;
import in.gov.jci.hrms.dto.CpfLoanEligibilityResponse;
import in.gov.jci.hrms.dto.CpfLoanSanctionRequest;
import in.gov.jci.hrms.dto.CpfResolvedRateDto;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * CPF Trust loan/withdrawal origination - applyLoan() (APPLIED) -> sanctionLoan() (SANCTIONED) ->
 * disburseLoan() (DISBURSED, debits the Trust ledger) - the write side of the cpf_loan_applications
 * lifecycle CpfLedgerSyncService already reads from for monthly payroll recovery (Head 30/51). See
 * CpfLoanApplication's own javadoc for why this is a separate table/lifecycle from the general-purpose
 * employee_loans.
 *
 * Statutory Cap Rule: a member may never be permitted more than 75% of their own running EE+VPF Trust
 * balance (employer JCPF share is deliberately excluded - it isn't the member's own money to borrow
 * against). Refundable Guard: a second REFUNDABLE_LOAN cannot be applied for while an earlier one (any of
 * APPLIED/SANCTIONED/DISBURSED) still carries a positive outstanding_balance - NON_REFUNDABLE_WITHDRAWAL
 * has no such one-at-a-time restriction, since it isn't a loan to be repaid.
 */
@Service
@Transactional(readOnly = true)
public class CpfLoanApplicationService {

    private static final BigDecimal SEVENTY_FIVE_PERCENT = new BigDecimal("0.75");
    private static final BigDecimal TWENTY_FOUR_HUNDRED = new BigDecimal("2400");
    private static final List<CpfLoanApplicationStatus> ACTIVE_STATUSES =
            List.of(CpfLoanApplicationStatus.APPLIED, CpfLoanApplicationStatus.SANCTIONED, CpfLoanApplicationStatus.DISBURSED);
    private static final List<CpfLoanApplicationStatus> PENDING_STATUSES =
            List.of(CpfLoanApplicationStatus.APPLIED, CpfLoanApplicationStatus.SANCTIONED);

    private final CpfLoanApplicationRepository loanRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final EmployeeRepository employeeRepository;
    private final CpfRateResolutionService rateResolutionService;

    public CpfLoanApplicationService(CpfLoanApplicationRepository loanRepository, CpfTrustMemberLedgerEntryRepository ledgerRepository,
                                      EmployeeRepository employeeRepository, CpfRateResolutionService rateResolutionService) {
        this.loanRepository = loanRepository;
        this.ledgerRepository = ledgerRepository;
        this.employeeRepository = employeeRepository;
        this.rateResolutionService = rateResolutionService;
    }

    public CpfLoanEligibilityResponse checkEligibility(Long employeeId, CpfLoanType loanType, String purpose) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        BigDecimal eeBalance = BigDecimal.ZERO;
        BigDecimal vpfBalance = BigDecimal.ZERO;
        var latest = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employeeId);
        if (latest.isPresent()) {
            eeBalance = latest.get().getRunningEeBalance();
            vpfBalance = latest.get().getRunningVpfBalance();
        }
        BigDecimal totalCorpus = eeBalance.add(vpfBalance);
        BigDecimal maxPermissible = totalCorpus.multiply(SEVENTY_FIVE_PERCENT).setScale(2, RoundingMode.HALF_UP);

        List<CpfLoanApplication> activeLoans = loanRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus()) && loan.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        boolean activeLoanExists = !activeLoans.isEmpty();
        BigDecimal outstandingActiveBalance = activeLoans.stream()
                .map(CpfLoanApplication::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String reason;
        if (loanType == CpfLoanType.REFUNDABLE_LOAN && activeLoanExists) {
            reason = "Not eligible for a new refundable loan - an existing loan with outstanding balance "
                    + outstandingActiveBalance + " is still active.";
        } else if (maxPermissible.compareTo(BigDecimal.ZERO) <= 0) {
            reason = "Not eligible - no CPF Trust corpus on record for this employee.";
        } else {
            reason = "Eligible for up to " + maxPermissible + " (75% of EE+VPF corpus " + totalCorpus + ").";
        }

        return new CpfLoanEligibilityResponse(employeeId, eeBalance, vpfBalance, totalCorpus, maxPermissible,
                activeLoanExists, outstandingActiveBalance, reason);
    }

    @Transactional
    public CpfLoanApplicationResponse applyLoan(CpfLoanApplicationRequest request, Long applicantUserId) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        CpfLoanEligibilityResponse eligibility = checkEligibility(request.employeeId(), request.loanType(), request.purpose());
        if (request.loanType() == CpfLoanType.REFUNDABLE_LOAN && eligibility.activeLoanExists()) {
            throw new BusinessRuleViolationException(eligibility.eligibilityReason());
        }
        if (request.appliedAmount().compareTo(eligibility.maxPermissibleAmount()) > 0) {
            throw new BusinessRuleViolationException(
                    "Applied amount " + request.appliedAmount() + " exceeds the maximum permissible amount of "
                            + eligibility.maxPermissibleAmount() + " (75% of EE+VPF corpus)");
        }

        CpfLoanApplication loan = new CpfLoanApplication(generateLoanApplicationNo(), employee, request.loanType(),
                request.purpose(), request.appliedAmount());
        loan.setApplicationReason(request.reason());
        loan.setTotalInstallments(request.totalInstallments());
        loan.setMonthlyRecoveryPrincipal(computeMonthlyRecovery(request.appliedAmount(), request.totalInstallments()));
        loan.setOutstandingBalance(BigDecimal.ZERO);
        loan.setStatus(CpfLoanApplicationStatus.APPLIED);

        return CpfLoanApplicationResponse.from(loanRepository.saveAndFlush(loan));
    }

    /**
     * Interest is computed with the standard GPF/CPF advance formula - total interest = (installments+1) *
     * sanctionedAmount * loanRate / 2400 (equivalent to applying the monthly rate to the average
     * outstanding balance over the repayment schedule) - using whichever rate CpfRateResolutionService
     * resolves for the sanction date's FY (applying the Para 60(2) preceding-FY fallback itself if this
     * FY's rate isn't notified yet), not a hardcoded "+1.00%": the loan markup is whatever
     * cpf_statutory_interest_rates.loan_markup_rate was actually notified for that FY.
     */
    @Transactional
    public CpfLoanApplicationResponse sanctionLoan(Long loanId, CpfLoanSanctionRequest request, Long approvingOfficerId) {
        CpfLoanApplication loan = findOrThrow(loanId);
        if (loan.getStatus() != CpfLoanApplicationStatus.APPLIED) {
            throw new BusinessRuleViolationException("CPF Loan Application " + loanId + " must be APPLIED to sanction but is " + loan.getStatus());
        }

        CpfLoanEligibilityResponse eligibility = checkEligibility(loan.getEmployee().getId(), loan.getLoanType(), loan.getPurpose());
        if (request.sanctionedAmount().compareTo(eligibility.maxPermissibleAmount()) > 0) {
            throw new BusinessRuleViolationException(
                    "Sanctioned amount " + request.sanctionedAmount() + " exceeds the maximum permissible amount of "
                            + eligibility.maxPermissibleAmount() + " (75% of EE+VPF corpus)");
        }

        String finYear = IncomingFundTransferService.financialYearFor(request.sanctionDate());
        CpfResolvedRateDto resolvedRate = rateResolutionService.resolveStatutoryRate(finYear);
        BigDecimal totalInterest = computeTotalInterest(request.sanctionedAmount(), request.totalInstallments(), resolvedRate.loanRate());
        BigDecimal monthlyInterest = totalInterest.divide(BigDecimal.valueOf(request.interestInstallments()), 2, RoundingMode.HALF_UP);

        loan.setSanctionedAmount(request.sanctionedAmount());
        loan.setSanctionOrderNo(request.sanctionOrderNo());
        loan.setSanctionDate(request.sanctionDate());
        loan.setBaseCpfRate(resolvedRate.baseRate());
        loan.setInterestRate(resolvedRate.loanRate());
        loan.setTotalInterestAmount(totalInterest);
        loan.setOutstandingBalance(request.sanctionedAmount());
        loan.setOutstandingInterest(totalInterest);
        loan.setTotalInstallments(request.totalInstallments());
        loan.setTotalInterestInstallments(request.interestInstallments());
        loan.setRecoveredInstallments(0);
        loan.setRecoveredInterestInstallments(0);
        loan.setMonthlyRecoveryPrincipal(computeMonthlyRecovery(request.sanctionedAmount(), request.totalInstallments()));
        loan.setMonthlyRecoveryInterest(monthlyInterest);
        loan.setRecoveryPhase(CpfLoanRecoveryPhase.PRINCIPAL);
        loan.setStatus(CpfLoanApplicationStatus.SANCTIONED);

        return CpfLoanApplicationResponse.from(loan);
    }

    private BigDecimal computeTotalInterest(BigDecimal sanctionedAmount, int principalInstallments, BigDecimal loanRate) {
        return BigDecimal.valueOf(principalInstallments + 1L)
                .multiply(sanctionedAmount)
                .multiply(loanRate)
                .divide(TWENTY_FOUR_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public CpfLoanApplicationResponse disburseLoan(Long loanId, Long disburseOfficerId) {
        CpfLoanApplication loan = findOrThrow(loanId);
        if (loan.getStatus() != CpfLoanApplicationStatus.SANCTIONED) {
            throw new BusinessRuleViolationException("CPF Loan Application " + loanId + " must be SANCTIONED to disburse but is " + loan.getStatus());
        }

        loan.setStatus(CpfLoanApplicationStatus.DISBURSED);
        loan.setDisbursedAt(Instant.now());
        loan.setOutstandingBalance(loan.getSanctionedAmount());
        loan.setRecoveredInstallments(0);

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

        BigDecimal eeShareDebit = loan.getSanctionedAmount().min(priorEe);
        BigDecimal vpfDebit = loan.getSanctionedAmount().subtract(eeShareDebit).max(BigDecimal.ZERO);

        LocalDate valueDate = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        BigDecimal newEe = priorEe.subtract(eeShareDebit);
        BigDecimal newVpf = priorVpf.subtract(vpfDebit);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_WITHDRAWAL,
                newEe, priorEr, newVpf, newEe.add(priorEr).add(newVpf));
        entry.setEeShareDebit(eeShareDebit);
        entry.setVpfDebit(vpfDebit);
        entry.setTotalDebit(eeShareDebit.add(vpfDebit));
        entry.setLoan(loan);
        entry.setReferenceDocNo(loan.getSanctionOrderNo());
        entry.setRemarks("CPF loan/withdrawal disbursed against application " + loan.getLoanApplicationNo());
        ledgerRepository.save(entry);

        return CpfLoanApplicationResponse.from(loan);
    }

    @Transactional
    public CpfLoanApplicationResponse rejectLoan(Long loanId, String remarks, Long rejectingOfficerId) {
        CpfLoanApplication loan = findOrThrow(loanId);
        if (loan.getStatus() != CpfLoanApplicationStatus.APPLIED) {
            throw new BusinessRuleViolationException("CPF Loan Application " + loanId + " must be APPLIED to reject but is " + loan.getStatus());
        }
        loan.setStatus(CpfLoanApplicationStatus.REJECTED);
        loan.setRejectionRemarks(remarks);
        return CpfLoanApplicationResponse.from(loan);
    }

    public List<CpfLoanApplicationResponse> findPending() {
        return loanRepository.findByStatusInOrderByCreatedAtDesc(PENDING_STATUSES).stream()
                .map(CpfLoanApplicationResponse::from).toList();
    }

    public List<CpfLoanApplicationResponse> findByEmployee(Long employeeId) {
        return loanRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(CpfLoanApplicationResponse::from).toList();
    }

    /** CpfLoansQueueTable's 4-tab filter (Pending Sanction/Pending Disbursement/Active Recoveries/Closed) and the hub page's metric cards - not part of the original spec's own CpfLoanController endpoint list, but both need an org-wide, any-status listing that /pending (APPLIED+SANCTIONED only) and /employee/{id} (one employee only) don't provide. */
    public org.springframework.data.domain.Page<CpfLoanApplicationResponse> findAll(CpfLoanApplicationStatus status,
                                                                                       org.springframework.data.domain.Pageable pageable) {
        var page = status != null ? loanRepository.findByStatus(status, pageable) : loanRepository.findAll(pageable);
        return page.map(CpfLoanApplicationResponse::from);
    }

    private CpfLoanApplication findOrThrow(Long id) {
        return loanRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("CPF Loan Application", id));
    }

    private BigDecimal computeMonthlyRecovery(BigDecimal amount, int totalInstallments) {
        return amount.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.HALF_UP);
    }

    /** "CPFL/{finYear}/{seq, 4 digits}" - same low-volume, human-paced-workflow rationale as IncomingFundTransferService's own voucher generator for why this isn't advisory-lock-guarded. */
    private String generateLoanApplicationNo() {
        String finYear = IncomingFundTransferService.financialYearFor(LocalDate.now());
        String prefix = "CPFL/" + finYear + "/";
        long seq = loanRepository.countByLoanApplicationNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }
}
