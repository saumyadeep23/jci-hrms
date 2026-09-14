package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCashRepaymentRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.dto.JciEccsLoanScheduleResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.HrmsPayrollCycle;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanInterestRate;
import in.gov.jci.hrms.entity.JciEccsLoanProduct;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanRepayment;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsMembershipStatus;
import in.gov.jci.hrms.entity.JciEccsRepaymentSource;
import in.gov.jci.hrms.entity.JciEccsScheduleStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanInterestRateRepository;
import in.gov.jci.hrms.repository.JciEccsLoanProductRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepaymentRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Owns the JCIECCS loan lifecycle. No separate sanction/disburse REST step exists in this module -
 * {@link #createLoan} records an already-sanctioned-and-disbursed loan in one step (Section 5 of the
 * spec lists only POST /api/jcieccs/loans, no sanction/disburse endpoints), reusing
 * {@link JciEccsAmortizationService} for the reducing-balance schedule and {@link CycleResolverService}
 * for the 26th-25th cycle/repayment-start resolution.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsLoanService {

    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final JciEccsMemberRepository memberRepository;
    private final JciEccsLoanProductRepository loanProductRepository;
    private final JciEccsLoanInterestRateRepository interestRateRepository;
    private final EmployeeRepository employeeRepository;
    private final CycleResolverService cycleResolverService;
    private final JciEccsAmortizationService amortizationService;
    private final JciEccsLifecycleEventService lifecycleEventService;
    private final JciEccsRecoveryService recoveryService;
    private final in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository collectionDetailRepository;
    private final in.gov.jci.hrms.repository.ExitClearanceRequestRepository exitClearanceRequestRepository;

    public JciEccsLoanService(JciEccsLoanRepository loanRepository, JciEccsLoanScheduleRepository scheduleRepository,
                               JciEccsMemberRepository memberRepository,
                               JciEccsLoanProductRepository loanProductRepository, JciEccsLoanInterestRateRepository interestRateRepository,
                               EmployeeRepository employeeRepository, CycleResolverService cycleResolverService,
                               JciEccsAmortizationService amortizationService, JciEccsLifecycleEventService lifecycleEventService,
                               JciEccsRecoveryService recoveryService, in.gov.jci.hrms.repository.JciEccsCollectionDetailRepository collectionDetailRepository,
                               in.gov.jci.hrms.repository.ExitClearanceRequestRepository exitClearanceRequestRepository) {
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.memberRepository = memberRepository;
        this.loanProductRepository = loanProductRepository;
        this.interestRateRepository = interestRateRepository;
        this.employeeRepository = employeeRepository;
        this.cycleResolverService = cycleResolverService;
        this.amortizationService = amortizationService;
        this.lifecycleEventService = lifecycleEventService;
        this.recoveryService = recoveryService;
        this.collectionDetailRepository = collectionDetailRepository;
        this.exitClearanceRequestRepository = exitClearanceRequestRepository;
    }

    @Transactional
    public JciEccsLoanResponse createLoan(JciEccsLoanCreateRequest request, Long performedByEmployeeId, String performedByUsername) {
        Employee employee = employeeRepository.findByEmployeeCode(request.employeeCode())
                .orElseThrow(() -> new BusinessRuleViolationException("No employee found with code " + request.employeeCode()));
        JciEccsMember member = memberRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("No JCIECCS membership found for employee " + request.employeeCode()));
        if (member.getMembershipStatus() != JciEccsMembershipStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Member " + member.getMembershipCode() + " is " + member.getMembershipStatus() + " - inactive members cannot receive loans");
        }
        // Phase 3 (spec section 27): freeze new loan eligibility once separation has been initiated -
        // reuses the existing HR exit-clearance open-request check (no second status engine).
        if (exitClearanceRequestRepository.existsByEmployeeIdAndStatusNot(employee.getId(),
                in.gov.jci.hrms.entity.ExitClearanceStatus.CANCELLED)) {
            throw new BusinessRuleViolationException(
                    "Employee " + request.employeeCode() + " has an open exit clearance request - new JCIECCS loans are frozen during separation");
        }

        JciEccsLoanProduct product = loanProductRepository.findByProductCode(request.productCode())
                .orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan Product", request.productCode()));
        if (!product.isActive()) {
            throw new BusinessRuleViolationException("JCIECCS Loan Product " + product.getProductCode() + " is not active");
        }
        if (request.sanctionedAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new BusinessRuleViolationException(
                    "Sanctioned amount " + request.sanctionedAmount() + " exceeds " + product.getProductCode()
                            + "'s configured ceiling of " + product.getMaxAmount());
        }
        int tenureMonths = request.tenureMonths() != null ? request.tenureMonths() : product.getMaxTenureMonths();
        if (tenureMonths <= 0 || tenureMonths > product.getMaxTenureMonths()) {
            throw new BusinessRuleViolationException(
                    "Tenure " + tenureMonths + " months exceeds " + product.getProductCode() + "'s maximum of " + product.getMaxTenureMonths());
        }

        JciEccsLoanInterestRate rate = interestRateRepository.findByLoanProduct_IdAndEffectiveToIsNull(product.getId())
                .orElseThrow(() -> new BusinessRuleViolationException("No active interest rate configured for " + product.getProductCode()));

        HrmsPayrollCycle disbursementCycle = cycleResolverService.resolveForDate(request.disbursementDate());
        HrmsPayrollCycle repaymentStartCycle = cycleResolverService.repaymentStartCycle(
                product.isRepaymentStartsInDisbursementCycle(), disbursementCycle);

        int standardInstallment = amortizationService.computeStandardPrincipalInstallment(request.sanctionedAmount(), tenureMonths);
        String loanIssueId = generateLoanIssueId(product.getProductCode());

        JciEccsLoan loan = new JciEccsLoan(member, product, loanIssueId, request.applicationDate(), request.sanctionDate(),
                request.disbursementDate(), disbursementCycle, request.sanctionedAmount(), request.sanctionedAmount(), tenureMonths,
                rate.getAnnualInterestRate(), standardInstallment, request.sanctionedAmount());
        loan.setCreatedBy(employee.getId());
        loan = loanRepository.saveAndFlush(loan);

        boolean doubleFirstInstallmentInterest = product.getProductCode() == JciEccsLoanProductCode.EMERGENCY;
        List<JciEccsLoanSchedule> schedule = amortizationService.generateSchedule(loan, repaymentStartCycle, doubleFirstInstallmentInterest);
        scheduleRepository.saveAll(schedule);

        JciEccsLoanResponse response = JciEccsLoanResponse.from(loan);
        lifecycleEventService.record("JCIECCS_LOAN", loan.getId(), "LOAN_CREATED", null, response, loanIssueId,
                performedByEmployeeId, "Created via POST /api/jcieccs/loans by " + performedByUsername);

        return response;
    }

    /** Phase 4 - the "Active Loans" list (spec section 56); ACTIVE-only by default since that's what
     * every dashboard/list consumer actually wants, with an escape hatch for the full history. */
    public List<JciEccsLoanResponse> list(JciEccsLoanStatus status) {
        List<JciEccsLoan> loans = status != null ? loanRepository.findByStatusOrderByCreatedAtDesc(status) : loanRepository.findAllByOrderByCreatedAtDesc();
        return loans.stream().map(JciEccsLoanResponse::from).toList();
    }

    public List<JciEccsLoanScheduleResponse> getSchedule(Long loanId) {
        if (!loanRepository.existsById(loanId)) {
            throw new MasterDataNotFoundException("JCIECCS Loan", loanId);
        }
        return scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loanId).stream().map(JciEccsLoanScheduleResponse::from).toList();
    }

    JciEccsLoan findOrThrow(Long loanId) {
        return loanRepository.findById(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
    }

    /**
     * POST /api/jcieccs/loans/{loanId}/repayments, source = CASH (spec section 2: "Support cash deposits
     * ... Transactionally recalculates remaining schedule tenures"). JCIECCS Lifecycle Engine Phase 1:
     * the recovery/allocation/ledger-posting/outstanding-balance effects are delegated to
     * {@link JciEccsRecoveryService#createAndPostCashRecovery} (idempotent on {@code request.idempotencyKey()}
     * - a retried/duplicated submission returns the original result rather than posting twice); this
     * method retains only what's still genuinely its own concern: capping the requested principal at
     * outstanding, the schedule-regeneration/closure mechanics (unchanged), and flagging
     * RECONCILIATION_REQUIRED on any payroll collection_detail for this loan that's already LOCKED but
     * not yet debit-confirmed (a cash deposit landing in that window risks double recovery once Payroll's
     * callback eventually arrives) - the locked amount itself is never silently touched.
     */
    @Transactional
    public JciEccsLoanResponse postCashRepayment(Long loanId, JciEccsCashRepaymentRequest request, Long performedByEmployeeId) {
        if (request.principalAmount().signum() == 0 && request.interestAmount().signum() == 0) {
            throw new BusinessRuleViolationException("At least one of principalAmount/interestAmount must be positive");
        }
        JciEccsLoan loan = loanRepository.findByIdForUpdate(loanId).orElseThrow(() -> new MasterDataNotFoundException("JCIECCS Loan", loanId));
        if (loan.getStatus() != JciEccsLoanStatus.ACTIVE) {
            throw new BusinessRuleViolationException("JCIECCS Loan " + loan.getLoanIssueId() + " must be ACTIVE but is " + loan.getStatus());
        }

        BigDecimal principalPaid = request.principalAmount().min(loan.getOutstandingPrincipal());
        HrmsPayrollCycle repaymentCycle = cycleResolverService.resolveForDate(request.repaymentDate());

        var result = recoveryService.createAndPostCashRecovery(loan, principalPaid, request.interestAmount(), request.repaymentDate(),
                repaymentCycle, request.referenceId(), request.idempotencyKey(), performedByEmployeeId);

        if (!result.alreadyPosted()) {
            if (loan.getStatus() == JciEccsLoanStatus.CLOSED) {
                scheduleRepository.deleteByLoan_IdAndStatusNot(loan.getId(), JciEccsScheduleStatus.PAID);
            } else {
                recalculateRemainingSchedule(loan);
            }
            flagLockedSnapshotIfAny(loan.getId());
            lifecycleEventService.record("JCIECCS_LOAN", loan.getId(), "CASH_REPAYMENT_POSTED", null, JciEccsLoanResponse.from(loan),
                    request.referenceId(), performedByEmployeeId, "Cash deposit of principal " + principalPaid + " / interest " + request.interestAmount());
        }

        return JciEccsLoanResponse.from(loan);
    }

    /** Spec section 19/35: a LOCKED, still-PENDING_DEBIT collection_detail for this loan is never silently
     * amended - it's flagged so an operator can reconcile it against the cash deposit that just landed. */
    private void flagLockedSnapshotIfAny(Long loanId) {
        for (var detail : collectionDetailRepository.findLockedPendingByLoanId(loanId)) {
            detail.setDebitStatus(in.gov.jci.hrms.entity.JciEccsDebitStatus.RECONCILIATION_REQUIRED);
            detail.setReconciliationReason("CASH_RECOVERY_AFTER_SNAPSHOT_LOCK");
            collectionDetailRepository.save(detail);
        }
    }

    private void recalculateRemainingSchedule(JciEccsLoan loan) {
        List<JciEccsLoanSchedule> existing = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loan.getId());
        int lastPaidInstallmentNo = existing.stream().filter(s -> s.getStatus() == JciEccsScheduleStatus.PAID)
                .mapToInt(JciEccsLoanSchedule::getInstallmentNo).max().orElse(0);
        HrmsPayrollCycle resumeCycle = existing.stream().filter(s -> s.getStatus() != JciEccsScheduleStatus.PAID)
                .min((a, b) -> Integer.compare(a.getInstallmentNo(), b.getInstallmentNo()))
                .map(JciEccsLoanSchedule::getCycle)
                .orElseGet(() -> cycleResolverService.resolveForYearMonth(java.time.YearMonth.now().getYear(), java.time.YearMonth.now().getMonthValue()));

        // Hibernate's default flush ordering runs pending inserts before pending deletes within one
        // flush - without an explicit flush here, the new installment-1/2/... rows below would collide
        // on (loan_id, installment_no) with the not-yet-physically-deleted old rows of the same numbers.
        scheduleRepository.deleteByLoan_IdAndStatusNot(loan.getId(), JciEccsScheduleStatus.PAID);
        scheduleRepository.flush();

        int standardInstallment = loan.getMonthlyPrincipalInstallment();
        int remainingInstallments = Math.max(
                loan.getOutstandingPrincipal().divide(BigDecimal.valueOf(standardInstallment), 0, RoundingMode.CEILING).intValueExact(), 1);

        List<JciEccsLoanSchedule> newSchedule = amortizationService.generateSchedule(loan, lastPaidInstallmentNo + 1, resumeCycle,
                loan.getOutstandingPrincipal(), remainingInstallments, false);
        scheduleRepository.saveAll(newSchedule);
    }

    private String generateLoanIssueId(JciEccsLoanProductCode productCode) {
        return switch (productCode) {
            case TERM -> "TE-%06d".formatted(loanRepository.nextTermLoanSequence());
            case EMERGENCY -> "EM-%06d".formatted(loanRepository.nextEmergencyLoanSequence());
        };
    }
}
