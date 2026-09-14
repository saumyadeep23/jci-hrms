package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfApplication;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import in.gov.jci.hrms.entity.CpfFrequencyScope;
import in.gov.jci.hrms.entity.CpfRuleHeadEligibility;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.repository.CpfApplicationRepository;
import in.gov.jci.hrms.repository.CpfRuleCeilingComponentRepository;
import in.gov.jci.hrms.repository.CpfRuleHeadEligibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The CPF Trust Loan &amp; Advances rule engine (Part 13 of the spec) - evaluates a
 * {@link CpfWithdrawalRuleDetail} (service eligibility, frequency/concurrency, ceiling formula, head debit
 * allocation) against caller-supplied facts. Contains no business VALUE of its own (no percentage,
 * service-month threshold, or factor) - every number comes from the rule/ceiling/head rows an approved
 * {@link CpfWithdrawalRuleVersion} carries, which already exist live on the shared dev database (see
 * {@link in.gov.jci.hrms.entity.CpfHeadMaster}'s own javadoc for how that schema was discovered).
 *
 * <h2>The CPF Head bridge (Part 7)</h2>
 * {@code cpf_trust_member_ledger_entries} has exactly three physical running-balance columns
 * (running_ee/vpf/er_balance) - not redesigned into a generalized N-head schema here, a much larger,
 * unrequested change. What this engine treats as fully configurable is which of those three heads (by
 * {@link in.gov.jci.hrms.entity.CpfHeadMaster#getCode()}) a rule allows and at what priority -
 * {@link #resolveEligibleHeadsOrdered} and {@link #allocateDebitAcrossHeads} never branch on a head code
 * themselves; the one place a head code is matched against a concrete balance is the caller-supplied
 * {@code headBalances} map's own keys (HEAD_A/HEAD_B/HEAD_C), built once by {@code CpfApplicationService}
 * from the ledger's own three columns.
 *
 * <h2>Ceiling combination is always MIN</h2>
 * The live schema has no per-rule "combine operator" column - every seeded rule's multiple ceiling
 * components are meant to be combined by taking their minimum (matching every one of the spec's own worked
 * examples: "Final Amount = MIN(...)"). This is therefore hardcoded here rather than configurable, since the
 * schema itself doesn't expose a choice.
 *
 * <h2>Interest and repayment-credit (Parts 10, 17)</h2>
 * This engine never computes loan interest itself - {@code CpfWithdrawalRuleDetail.getInterestMethod()} is
 * a reference key naming which EXISTING calculation to use ("JCI_TRUST_PRINCIPAL_FIRST" means
 * {@code CpfLoanApplicationService}'s own principal-then-interest advance formula, reused directly - see
 * {@code CpfApplicationService}). Similarly, {@code repaymentCreditMethod} is fully configurable and
 * displayed, but only ORIGINAL_DEBIT_HEAD (the value every live seed row already uses) is actually wired
 * into a live re-credit posting path; {@link #requireImplementedRepaymentMethod} rejects the other four
 * rather than silently applying unverified behavior.
 */
@Service
@Transactional(readOnly = true)
public class CpfWithdrawalRuleEngine {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final List<CpfApplicationStatus> SUCCESSFULLY_PROCESSED = List.of(CpfApplicationStatus.DISBURSED, CpfApplicationStatus.CLOSED);
    private static final List<CpfApplicationStatus> ACTIVE_STATUSES =
            List.of(CpfApplicationStatus.APPLIED, CpfApplicationStatus.SANCTIONED, CpfApplicationStatus.DISBURSED);

    private final CpfRuleHeadEligibilityRepository ruleHeadRepository;
    private final CpfRuleCeilingComponentRepository ruleCeilingRepository;
    private final CpfApplicationRepository applicationRepository;

    public CpfWithdrawalRuleEngine(CpfRuleHeadEligibilityRepository ruleHeadRepository, CpfRuleCeilingComponentRepository ruleCeilingRepository,
                                    CpfApplicationRepository applicationRepository) {
        this.ruleHeadRepository = ruleHeadRepository;
        this.ruleCeilingRepository = ruleCeilingRepository;
        this.applicationRepository = applicationRepository;
    }

    /** Facts a caller (CpfApplicationService, or the Rule Simulator) supplies - this engine never looks any of these up itself. */
    public record CeilingEvaluationContext(
            BigDecimal basicPlusDa,
            Map<String, BigDecimal> headBalances,
            BigDecimal propertyCost,
            BigDecimal payrollDeductionCapacity,
            BigDecimal outstandingLoan
    ) {
        public CeilingEvaluationContext {
            basicPlusDa = basicPlusDa == null ? BigDecimal.ZERO : basicPlusDa;
            headBalances = headBalances == null ? Map.of() : headBalances;
            propertyCost = propertyCost == null ? BigDecimal.ZERO : propertyCost;
            payrollDeductionCapacity = payrollDeductionCapacity == null ? BigDecimal.ZERO : payrollDeductionCapacity;
            outstandingLoan = outstandingLoan == null ? BigDecimal.ZERO : outstandingLoan;
        }
    }

    public record CeilingResult(BigDecimal finalAmount, BigDecimal totalEligibleBalance, List<CeilingComponentValue> components, List<String> trace) {
    }

    /** One evaluated ceiling component (Part 11) - structured sibling of the free-text trace line, for
     * callers (the Loan Simulator, Apply for Loan) that need to render "component / source metric / value"
     * as real fields rather than parsing trace strings. */
    public record CeilingComponentValue(String componentName, String sourceMetric, BigDecimal calculatedValue) {
    }

    public record ServiceEligibilityResult(boolean eligible, String reason) {
    }

    public record FrequencyResult(boolean eligible, String reason, long usedCount) {
    }

    public record HeadAllocation(Map<String, BigDecimal> debitByHeadCode, List<String> trace) {
    }

    /** Eligible heads (Part 8) in debit order (Part 9). */
    public List<CpfRuleHeadEligibility> resolveEligibleHeadsOrdered(CpfWithdrawalRuleDetail detail) {
        return ruleHeadRepository.findByDetail_IdAndEligibleTrueOrderByDebitPriorityAsc(detail.getId());
    }

    /**
     * Continuous-service eligibility (Part 14). dateOfJoining is the only service-start fact this codebase
     * has; when includePreviousService is set, {@link Employee#getPriorQualifyingServiceDays()} (an
     * existing, already-populated field) is folded in by shifting the effective join date backward, so a
     * Period comparison against minServiceMonths stays exact rather than an approximate day-count.
     * allowBreakInService is stored/displayed but not separately enforced - this codebase has no
     * break-in-service tracking model to evaluate it against; disclosed as a known gap, not fabricated.
     */
    public ServiceEligibilityResult evaluateServiceEligibility(CpfWithdrawalRuleDetail detail, Employee employee, LocalDate asOf) {
        if (detail.getMinServiceMonths() <= 0) {
            return new ServiceEligibilityResult(true, "No minimum service required.");
        }
        LocalDate joinDate = employee.getDateOfJoining();
        if (joinDate == null) {
            return new ServiceEligibilityResult(false, "Employee has no recorded date of joining - cannot evaluate service eligibility.");
        }
        LocalDate effectiveJoinDate = detail.isIncludePreviousService()
                ? joinDate.minusDays(employee.getPriorQualifyingServiceDays())
                : joinDate;
        Period served = Period.between(effectiveJoinDate, asOf);
        int servedMonths = served.getYears() * 12 + served.getMonths();
        boolean eligible = servedMonths >= detail.getMinServiceMonths();
        String reason = eligible
                ? "Completed " + servedMonths + " months' service (>= required " + detail.getMinServiceMonths() + ")."
                : "Only " + servedMonths + " months' service completed - requires " + detail.getMinServiceMonths() + ".";
        return new ServiceEligibilityResult(eligible, reason);
    }

    /**
     * Frequency (max_occurrences over frequency_scope, Part 15) AND concurrency (max_active_concurrency,
     * generalizing Part 15's "Refundable loan: concurrency = 1" to every purpose) combined into one gate.
     * Frequency counts only DISBURSED/CLOSED applications (successfully processed, per the spec's own
     * wording); concurrency counts any not-yet-terminal (APPLIED/SANCTIONED/DISBURSED) application.
     */
    public FrequencyResult evaluateFrequency(CpfWithdrawalRuleDetail detail, String employeeCode, java.util.UUID purposeId, LocalDate asOf) {
        long activeCount = applicationRepository.findByEmployeeCodeAndPurpose_IdAndStatusIn(employeeCode, purposeId, ACTIVE_STATUSES).size();
        if (activeCount >= detail.getMaxActiveConcurrency()) {
            return new FrequencyResult(false, "Maximum concurrent applications (" + detail.getMaxActiveConcurrency()
                    + ") already reached for this purpose - " + activeCount + " active.", activeCount);
        }

        if (detail.getFrequencyScope() == CpfFrequencyScope.NONE) {
            return new FrequencyResult(true, "No frequency limit configured for this rule.", 0);
        }
        List<CpfApplication> processed = applicationRepository.findByEmployeeCodeAndPurpose_IdAndStatusIn(employeeCode, purposeId, SUCCESSFULLY_PROCESSED);

        LocalDate windowStart = switch (detail.getFrequencyScope()) {
            case FINANCIAL_YEAR -> LocalDate.of(asOf.getMonthValue() >= 4 ? asOf.getYear() : asOf.getYear() - 1, 4, 1);
            case CALENDAR_YEAR -> LocalDate.of(asOf.getYear(), 1, 1);
            case ROLLING_PERIOD -> asOf.minusMonths(12);
            case SERVICE, NONE -> LocalDate.MIN;
        };

        long usedCount = processed.stream()
                .filter(a -> a.getCreatedAt() != null && !toLocalDate(a.getCreatedAt()).isBefore(windowStart))
                .count();
        boolean eligible = usedCount < detail.getMaxOccurrences();
        String reason = eligible
                ? "Used " + usedCount + " of " + detail.getMaxOccurrences() + " permitted (" + detail.getFrequencyScope() + ")."
                : "Frequency limit of " + detail.getMaxOccurrences() + " (" + detail.getFrequencyScope() + ") already reached (" + usedCount + " used).";
        return new FrequencyResult(eligible, reason, usedCount);
    }

    private static LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * The ceiling formula (Parts 11, 13): evaluates every configured component, combines them via MIN, then
     * applies balance retention (Part 12) as a final cap - never as just another component, so it always
     * protects the member's minimum retained corpus regardless of the rest of the formula. An empty
     * component list is treated as "no ceiling beyond the eligible balance itself", not a zero ceiling.
     */
    public CeilingResult evaluateCeiling(CpfWithdrawalRuleDetail detail, CeilingEvaluationContext context) {
        List<String> eligibleHeadCodes = resolveEligibleHeadsOrdered(detail).stream().map(h -> h.getHead().getCode()).toList();
        BigDecimal totalEligibleBalance = eligibleHeadCodes.stream()
                .map(code -> context.headBalances().getOrDefault(code, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> trace = new ArrayList<>();
        trace.add("Total eligible balance (" + String.join("+", eligibleHeadCodes) + ") = " + totalEligibleBalance);

        var components = ruleCeilingRepository.findByDetail_IdOrderByDisplayOrderAsc(detail.getId());
        List<BigDecimal> componentValues = new ArrayList<>();
        List<CeilingComponentValue> componentBreakdown = new ArrayList<>();
        for (var component : components) {
            BigDecimal metric = switch (component.getSourceMetric()) {
                case ELIGIBLE_BALANCE -> totalEligibleBalance;
                case BASIC_PLUS_DA -> context.basicPlusDa();
                case PROPERTY_COST -> context.propertyCost();
                case OUTSTANDING_LOAN -> context.outstandingLoan();
                case PAYROLL_DEDUCTION_CAPACITY -> context.payrollDeductionCapacity();
                case FIXED_AMOUNT -> BigDecimal.ZERO;
            };
            BigDecimal factor = component.getFactorValue() != null ? component.getFactorValue() : BigDecimal.ZERO;
            BigDecimal componentValue = switch (component.getOperator()) {
                case MULTIPLY -> metric.multiply(factor);
                case PERCENTAGE -> metric.multiply(factor).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
                case FIXED -> factor;
            };
            componentValues.add(componentValue);
            componentBreakdown.add(new CeilingComponentValue(component.getComponentName(), component.getSourceMetric().name(), componentValue));
            trace.add(component.getComponentName() + " = " + componentValue);
        }

        BigDecimal combined;
        if (componentValues.isEmpty()) {
            combined = totalEligibleBalance;
            trace.add("No ceiling components configured - defaulting to total eligible balance.");
        } else {
            combined = componentValues.stream().min(BigDecimal::compareTo).orElseThrow();
            trace.add("MIN of the above components = " + combined);
        }

        if (detail.getBalanceRetentionPct().signum() > 0) {
            BigDecimal retentionCap = totalEligibleBalance
                    .multiply(ONE_HUNDRED.subtract(detail.getBalanceRetentionPct()))
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
            if (retentionCap.compareTo(combined) < 0) {
                trace.add("Mandatory balance retention (" + detail.getBalanceRetentionPct() + "%) caps the withdrawal at " + retentionCap);
                combined = retentionCap;
            }
        }

        combined = combined.max(BigDecimal.ZERO);
        trace.add("Final eligible amount = " + combined);
        return new CeilingResult(combined, totalEligibleBalance, componentBreakdown, trace);
    }

    /**
     * Allocates a withdrawal amount across eligible heads in configured debit-priority order (Part 9),
     * never taking more from a head than that head's own available balance. Any shortfall is surfaced as a
     * trace warning rather than silently truncated without explanation.
     */
    public HeadAllocation allocateDebitAcrossHeads(CpfWithdrawalRuleDetail detail, BigDecimal amount, Map<String, BigDecimal> headBalances) {
        Map<String, BigDecimal> debit = new LinkedHashMap<>();
        List<String> trace = new ArrayList<>();
        BigDecimal remaining = amount;
        for (CpfRuleHeadEligibility ruleHead : resolveEligibleHeadsOrdered(detail)) {
            String code = ruleHead.getHead().getCode();
            BigDecimal available = headBalances.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal take = remaining.min(available).max(BigDecimal.ZERO);
            debit.put(code, take);
            trace.add(code + " debit = " + take + " (available " + available + ", priority " + ruleHead.getDebitPriority() + ")");
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            trace.add("WARNING: " + remaining + " could not be allocated - eligible head balances are insufficient for the full amount.");
        }
        return new HeadAllocation(debit, trace);
    }

    /**
     * Only ORIGINAL_DEBIT_HEAD has an actual, verified posting-path implementation today. Throws for the
     * other four configurable-but-unimplemented values rather than silently running an unverified
     * recovery-crediting behavior against real CPF Trust member balances.
     */
    public void requireImplementedRepaymentMethod(CpfWithdrawalRuleDetail detail) {
        if (detail.getRepaymentCreditMethod() != in.gov.jci.hrms.entity.CpfRepaymentCreditMethod.ORIGINAL_DEBIT_HEAD) {
            throw new in.gov.jci.hrms.exception.BusinessRuleViolationException(
                    "Repayment credit method " + detail.getRepaymentCreditMethod() + " is configured but not yet implemented in the "
                            + "recovery posting path - only ORIGINAL_DEBIT_HEAD is live today.");
        }
    }
}
