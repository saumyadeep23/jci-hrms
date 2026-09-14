package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsRestructureRequest;
import in.gov.jci.hrms.dto.JciEccsTopUpRequest;
import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanInterestRate;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.JciEccsLoanInterestRateRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Restructure and Top-Up share one underlying operation (spec section 5): archive the current loan
 * (status -&gt; RESTRUCTURED, its schedule/ledger rows left untouched - real history, not deleted) and
 * generate a brand-new {@link JciEccsLoan} (parent_loan_id -&gt; the old one) with a freshly-computed
 * schedule via {@link JciEccsAmortizationService}. Restructure has no eligibility gate beyond "the loan
 * is currently ACTIVE"; Top-Up additionally requires &gt;= 60.00% of the loan's own sanctioned amount
 * already repaid and caps the new total exposure at the product's configured ceiling.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsRestructureService {

    private static final BigDecimal TOPUP_MIN_REPAID_PCT = new BigDecimal("60.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final JciEccsLoanInterestRateRepository interestRateRepository;
    private final CycleResolverService cycleResolverService;
    private final JciEccsAmortizationService amortizationService;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsRestructureService(JciEccsLoanRepository loanRepository, JciEccsLoanScheduleRepository scheduleRepository,
                                      JciEccsLoanInterestRateRepository interestRateRepository, CycleResolverService cycleResolverService,
                                      JciEccsAmortizationService amortizationService, JciEccsLifecycleEventService lifecycleEventService) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.interestRateRepository = interestRateRepository;
        this.cycleResolverService = cycleResolverService;
        this.amortizationService = amortizationService;
        this.lifecycleEventService = lifecycleEventService;
    }

    @Transactional
    public JciEccsLoanResponse restructure(Long loanId, JciEccsRestructureRequest request, Long performedByEmployeeId) {
        JciEccsLoan oldLoan = lockActiveLoanOrThrow(loanId);
        JciEccsLoan newLoan = regenerateAsNewLoan(oldLoan, oldLoan.getOutstandingPrincipal(), request.tenureMonths(),
                request.effectiveDate(), true, false, performedByEmployeeId);
        return JciEccsLoanResponse.from(newLoan);
    }

    @Transactional
    public JciEccsLoanResponse topUp(Long loanId, JciEccsTopUpRequest request, Long performedByEmployeeId) {
        JciEccsLoan oldLoan = lockActiveLoanOrThrow(loanId);

        if (!oldLoan.getLoanProduct().isTopupAllowed()) {
            throw new BusinessRuleViolationException("Top-up is not allowed for product " + oldLoan.getLoanProduct().getProductCode());
        }

        BigDecimal repaid = oldLoan.getSanctionedAmount().subtract(oldLoan.getOutstandingPrincipal());
        BigDecimal repaidPct = repaid.multiply(HUNDRED).divide(oldLoan.getSanctionedAmount(), 2, RoundingMode.HALF_UP);
        if (repaidPct.compareTo(TOPUP_MIN_REPAID_PCT) < 0) {
            throw new BusinessRuleViolationException(
                    "Top-up requires at least 60.00% of the sanctioned amount repaid - only " + repaidPct + "% repaid so far");
        }

        BigDecimal newExposure = oldLoan.getOutstandingPrincipal().add(request.topUpAmount());
        if (newExposure.compareTo(oldLoan.getLoanProduct().getMaxAmount()) > 0) {
            throw new BusinessRuleViolationException("New exposure " + newExposure + " would exceed "
                    + oldLoan.getLoanProduct().getProductCode() + "'s configured ceiling of " + oldLoan.getLoanProduct().getMaxAmount());
        }

        JciEccsLoan newLoan = regenerateAsNewLoan(oldLoan, newExposure, request.tenureMonths(), request.effectiveDate(), false, true,
                performedByEmployeeId);
        return JciEccsLoanResponse.from(newLoan);
    }

    private JciEccsLoan lockActiveLoanOrThrow(Long loanId) {
        JciEccsLoan loan = loanRepository.findByIdForUpdate(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
        if (loan.getStatus() != JciEccsLoanStatus.ACTIVE) {
            throw new BusinessRuleViolationException("JCIECCS Loan " + loan.getLoanIssueId() + " must be ACTIVE but is " + loan.getStatus());
        }
        return loan;
    }

    private JciEccsLoan regenerateAsNewLoan(JciEccsLoan oldLoan, BigDecimal newSanctionedAmount, int tenureMonths, LocalDate effectiveDate,
                                             boolean isRestructure, boolean isTopUp, Long performedByEmployeeId) {
        JciEccsLoanInterestRate rate = interestRateRepository.findByLoanProduct_IdAndEffectiveToIsNull(oldLoan.getLoanProduct().getId())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active interest rate configured for " + oldLoan.getLoanProduct().getProductCode()));

        HrmsPayrollCycle disbursementCycle = cycleResolverService.resolveForDate(effectiveDate);
        HrmsPayrollCycle repaymentStartCycle = cycleResolverService.repaymentStartCycle(
                oldLoan.getLoanProduct().isRepaymentStartsInDisbursementCycle(), disbursementCycle);

        int standardInstallment = amortizationService.computeStandardPrincipalInstallment(newSanctionedAmount, tenureMonths);
        String loanIssueId = generateLoanIssueId(oldLoan.getLoanProduct().getProductCode());

        JciEccsLoan newLoan = new JciEccsLoan(oldLoan.getMember(), oldLoan.getLoanProduct(), loanIssueId, effectiveDate, effectiveDate,
                effectiveDate, disbursementCycle, newSanctionedAmount, newSanctionedAmount, tenureMonths, rate.getAnnualInterestRate(),
                standardInstallment, newSanctionedAmount);
        newLoan.setParentLoan(oldLoan);
        newLoan.setRestructuringCount(oldLoan.getRestructuringCount() + (isRestructure ? 1 : 0));
        newLoan.setTopupCount(oldLoan.getTopupCount() + (isTopUp ? 1 : 0));
        newLoan.setCreatedBy(performedByEmployeeId);
        newLoan = loanRepository.saveAndFlush(newLoan);

        boolean doubleFirstInstallmentInterest = oldLoan.getLoanProduct().getProductCode() == JciEccsLoanProductCode.EMERGENCY;
        List<JciEccsLoanSchedule> schedule = amortizationService.generateSchedule(newLoan, repaymentStartCycle, doubleFirstInstallmentInterest);
        scheduleRepository.saveAll(schedule);

        String reason = isTopUp ? "Topped up into " + loanIssueId : "Restructured into " + loanIssueId;
        oldLoan.setStatus(JciEccsLoanStatus.RESTRUCTURED);
        oldLoan.setClosedDate(effectiveDate);
        oldLoan.setClosureReason(reason);
        loanRepository.save(oldLoan);

        String eventType = isTopUp ? "LOAN_TOPPED_UP" : "LOAN_RESTRUCTURED";
        lifecycleEventService.record("JCIECCS_LOAN", newLoan.getId(), eventType, JciEccsLoanResponse.from(oldLoan),
                JciEccsLoanResponse.from(newLoan), oldLoan.getLoanIssueId(), performedByEmployeeId, reason);

        return newLoan;
    }

    private String generateLoanIssueId(JciEccsLoanProductCode productCode) {
        return switch (productCode) {
            case TERM -> "TE-%06d".formatted(loanRepository.nextTermLoanSequence());
            case EMERGENCY -> "EM-%06d".formatted(loanRepository.nextEmergencyLoanSequence());
        };
    }
}
