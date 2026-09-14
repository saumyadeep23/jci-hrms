package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.ExitClearanceDepartment;
import in.gov.jci.hrms.entity.ExitClearanceItem;
import in.gov.jci.hrms.entity.ExitClearanceItemStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.ExitClearanceStatus;
import in.gov.jci.hrms.entity.JciEccsLoan;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsLoanStatus;
import in.gov.jci.hrms.entity.JciEccsLoanSchedule;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.JciEccsSettlement;
import in.gov.jci.hrms.entity.JciEccsThriftTransaction;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.ExitClearanceItemRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
import in.gov.jci.hrms.repository.JciEccsLoanScheduleRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.JciEccsSettlementRepository;
import in.gov.jci.hrms.repository.JciEccsThriftTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * JCIECCS Lifecycle Engine Phase 3 - the cooperative liability position for employee separation (spec
 * section 25-40). Deliberately does NOT build a second separation/settlement/gratuity engine: the
 * authoritative HOLD/CLEARED state is the existing {@link ExitClearanceItem} row for the JCIECCS
 * department (added to {@link ExitClearanceDepartment} in this phase), cleared via the existing
 * {@link ExitClearanceService#updateDepartmentClearance}; this service only calculates the JCIECCS
 * figure that feeds it and never itself closes a loan, zeroes a balance, or waives dues (spec section 28).
 *
 * <p>{@code setoffAmount} is always zero. No approved rule for netting share/fund/security/thrift
 * against outstanding TE/EM dues exists anywhere in this codebase (searched: no JciEccs*SetOff*,
 * no cooperative bylaws configuration) - inventing a percentage or priority here would be exactly the
 * "invented JCIECCS rule" the spec explicitly forbids. Net liability is therefore always the gross
 * outstanding loan liability; the raw share/fund/security/thrift balances are reported alongside it so a
 * human can apply the actual cooperative bylaws manually. This is marked REQUIRES_BUSINESS_CONFIRMATION
 * in the final report, not resolved here.
 */
@Service
@Transactional(readOnly = true)
public class JciEccsNoDuesService {

    private final JciEccsMemberRepository memberRepository;
    private final JciEccsLoanRepository loanRepository;
    private final JciEccsLoanScheduleRepository scheduleRepository;
    private final JciEccsThriftTransactionRepository thriftTransactionRepository;
    private final JciEccsSettlementRepository settlementRepository;
    private final ExitClearanceRequestRepository clearanceRequestRepository;
    private final ExitClearanceItemRepository clearanceItemRepository;
    private final ExitClearanceService exitClearanceService;
    private final JciEccsLifecycleEventService lifecycleEventService;

    public JciEccsNoDuesService(JciEccsMemberRepository memberRepository, JciEccsLoanRepository loanRepository,
                                 JciEccsLoanScheduleRepository scheduleRepository, JciEccsThriftTransactionRepository thriftTransactionRepository,
                                 JciEccsSettlementRepository settlementRepository, ExitClearanceRequestRepository clearanceRequestRepository,
                                 ExitClearanceItemRepository clearanceItemRepository, ExitClearanceService exitClearanceService,
                                 JciEccsLifecycleEventService lifecycleEventService) {
        this.memberRepository = memberRepository;
        this.loanRepository = loanRepository;
        this.scheduleRepository = scheduleRepository;
        this.thriftTransactionRepository = thriftTransactionRepository;
        this.settlementRepository = settlementRepository;
        this.clearanceRequestRepository = clearanceRequestRepository;
        this.clearanceItemRepository = clearanceItemRepository;
        this.exitClearanceService = exitClearanceService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * Recomputes the member's full position and refreshes their one settlement snapshot row (never
     * duplicated - spec section 37's "traceable snapshot"). If an open exit clearance request already
     * exists for this employee, links to it and its JCIECCS item; if that item is already CLEARED and
     * the freshly-computed net liability differs from what was cleared, the snapshot is marked stale
     * (spec section 37) but the already-finalized item is left untouched - never silently reopened.
     */
    @Transactional
    public JciEccsSettlement calculate(Long employeeId, Long performedByEmployeeId) {
        JciEccsMember member = memberRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new BusinessRuleViolationException("No JCIECCS membership found for employee " + employeeId));

        BigDecimal thriftBalance = thriftTransactionRepository.findTopByMember_IdOrderByIdDesc(member.getId())
                .map(JciEccsThriftTransaction::getBalanceAfter).orElse(BigDecimal.ZERO);

        BigDecimal[] term = outstandingForProduct(member.getId(), JciEccsLoanProductCode.TERM);
        BigDecimal[] emergency = outstandingForProduct(member.getId(), JciEccsLoanProductCode.EMERGENCY);
        BigDecimal otherDues = BigDecimal.ZERO; // no other JCIECCS due type is modeled in this codebase

        BigDecimal netLiability = term[0].add(term[1]).add(emergency[0]).add(emergency[1]).add(otherDues);

        JciEccsSettlement settlement = settlementRepository.findByMember_Id(member.getId())
                .orElseGet(() -> new JciEccsSettlement(member, employeeId));
        boolean wasClearedAtOldFigure = settlement.getExitClearanceItem() != null
                && settlement.getExitClearanceItem().getStatus() == ExitClearanceItemStatus.CLEARED
                && settlement.getNetLiability().compareTo(netLiability) != 0;

        settlement.refresh(member.getShareBalance(), member.getFundBalance(), member.getSecurityBalance(), thriftBalance,
                term[0], term[1], emergency[0], emergency[1], otherDues, netLiability, performedByEmployeeId);

        Optional<ExitClearanceRequest> openRequest =
                clearanceRequestRepository.findFirstByEmployeeIdAndStatusNotOrderByInitiatedDateDesc(employeeId, ExitClearanceStatus.CANCELLED);
        if (openRequest.isPresent()) {
            ExitClearanceItem jcieccsItem = clearanceItemRepository
                    .findByClearanceRequestIdAndDepartmentCode(openRequest.get().getId(), ExitClearanceDepartment.JCIECCS).orElse(null);
            settlement.linkExitClearance(openRequest.get(), jcieccsItem, openRequest.get().getSeparationType().name(),
                    openRequest.get().getTargetReleaseDate());
            if (jcieccsItem != null && jcieccsItem.getStatus() != ExitClearanceItemStatus.CLEARED) {
                // Not yet finalized - safe to keep the item's own figure in sync with the latest calculation.
                jcieccsItem.setDuesRecoveryAmount(netLiability);
            }
        }
        settlement.setStale(wasClearedAtOldFigure);

        settlement = settlementRepository.save(settlement);

        lifecycleEventService.record("JCIECCS_MEMBER", member.getId(), "JCIECCS_SETTLEMENT_CALCULATED", null, netLiability,
                null, performedByEmployeeId, "Net liability " + netLiability + (wasClearedAtOldFigure ? " (stale vs already-cleared amount)" : ""));

        return settlement;
    }

    public JciEccsSettlement getOrCalculate(Long employeeId, Long performedByEmployeeId) {
        return settlementRepository.findByEmployeeId(employeeId).orElseGet(() -> calculate(employeeId, performedByEmployeeId));
    }

    /**
     * Clears the JCIECCS department item on the employee's open exit clearance request via the existing
     * {@link ExitClearanceService#updateDepartmentClearance} (never a duplicate clearance mechanism).
     * Always recalculates first (spec section 37 - "do not silently release using stale amounts") and
     * blocks clearance while net liability &gt; 0, since no approved set-off rule exists to justify
     * clearing with an outstanding balance.
     */
    @Transactional
    public ExitClearanceItem clear(Long employeeId, String remarks, Long performedByEmployeeId) {
        JciEccsSettlement fresh = calculate(employeeId, performedByEmployeeId);
        if (fresh.getExitClearanceRequest() == null) {
            throw new BusinessRuleViolationException("Employee " + employeeId + " has no open exit clearance request to clear against");
        }
        if (fresh.getExitClearanceItem() == null) {
            throw new BusinessRuleViolationException(
                    "Exit clearance request " + fresh.getExitClearanceRequest().getId() + " has no JCIECCS department item");
        }
        if (fresh.getNetLiability().signum() > 0) {
            throw new BusinessRuleViolationException(
                    "Cannot clear JCIECCS no-dues: outstanding liability of " + fresh.getNetLiability() + " remains");
        }

        ExitClearanceItem cleared = exitClearanceService.updateDepartmentClearance(fresh.getExitClearanceItem().getId(),
                ExitClearanceItemStatus.CLEARED, BigDecimal.ZERO, remarks, performedByEmployeeId);
        fresh.setStale(false);
        settlementRepository.save(fresh);

        lifecycleEventService.record("JCIECCS_MEMBER", fresh.getMember().getId(), "JCIECCS_CLEARED", null, BigDecimal.ZERO,
                null, performedByEmployeeId, remarks);

        return cleared;
    }

    /** [term_principal_outstanding, term_interest_outstanding] style pair for whichever product; the
     * "current" loan is this module's own existing single-active-loan-per-product convention
     * (JciEccsLoanRepository#findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc). */
    private BigDecimal[] outstandingForProduct(Long memberId, JciEccsLoanProductCode productCode) {
        Optional<JciEccsLoan> loan = loanRepository.findFirstByMember_IdAndLoanProduct_ProductCodeAndStatusOrderByCreatedAtDesc(
                memberId, productCode, JciEccsLoanStatus.ACTIVE);
        if (loan.isEmpty()) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        BigDecimal principalOutstanding = loan.get().getOutstandingPrincipal();
        List<JciEccsLoanSchedule> schedule = scheduleRepository.findByLoan_IdOrderByInstallmentNoAsc(loan.get().getId());
        BigDecimal interestOutstanding = schedule.stream()
                .map(s -> s.getInterestDue().subtract(s.getInterestPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .max(BigDecimal.ZERO);
        return new BigDecimal[]{principalOutstanding, interestOutstanding};
    }
}
