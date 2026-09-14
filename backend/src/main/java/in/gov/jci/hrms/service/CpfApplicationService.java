package in.gov.jci.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.gov.jci.hrms.dto.CeilingComponentResponse;
import in.gov.jci.hrms.dto.CpfApplicationDocumentSubmission;
import in.gov.jci.hrms.dto.CpfApplicationEligibilityResponse;
import in.gov.jci.hrms.dto.CpfApplicationRequest;
import in.gov.jci.hrms.dto.CpfApplicationResponse;
import in.gov.jci.hrms.dto.CpfApplicationSanctionRequest;
import in.gov.jci.hrms.dto.CpfRuleDocumentConfig;
import in.gov.jci.hrms.dto.HeadAllocationResponse;
import in.gov.jci.hrms.dto.RepaymentPreviewResponse;
import in.gov.jci.hrms.entity.CpfApplication;
import in.gov.jci.hrms.entity.CpfApplicationLedgerAllocation;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import in.gov.jci.hrms.entity.CpfHeadMaster;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfRuleDocument;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.CpfWithdrawalPurposeMaster;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.repository.CpfApplicationLedgerAllocationRepository;
import in.gov.jci.hrms.repository.CpfApplicationRepository;
import in.gov.jci.hrms.repository.CpfHeadMasterRepository;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfRuleDocumentRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalPurposeMasterRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rule-driven CPF withdrawal/loan application lifecycle - apply() (APPLIED) -&gt; sanction()
 * (SANCTIONED) -&gt; disburse() (DISBURSED, debits the real Trust ledger) - operating on
 * {@link CpfApplication} (the already-live, purpose-built UUID table for this flow - see
 * {@link in.gov.jci.hrms.entity.CpfHeadMaster}'s own javadoc). Deliberately separate from
 * {@link CpfLoanApplicationService}/{@code cpf_loan_applications} (untouched by this change) - see
 * {@link CpfApplication}'s own javadoc for why.
 *
 * <p>Every eligibility/ceiling/frequency/head-allocation decision is delegated to
 * {@link CpfWithdrawalRuleEngine} against whichever {@link CpfWithdrawalRuleVersion} is currently APPROVED
 * and effective for the application's purpose ({@link CpfWithdrawalRuleService#resolveActiveVersion}) - no
 * business value is hardcoded here. Interest (when the resolved rule has one configured) reuses
 * {@link CpfLoanApplicationService#computeTotalInterest} directly rather than re-implementing it (Part 17:
 * "do NOT create a second CPF interest engine").
 */
@Service
@Transactional(readOnly = true)
public class CpfApplicationService {

    private static final List<CpfApplicationStatus> ACTIVE_STATUSES =
            List.of(CpfApplicationStatus.APPLIED, CpfApplicationStatus.SANCTIONED, CpfApplicationStatus.DISBURSED);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CpfApplicationRepository applicationRepository;
    private final CpfApplicationLedgerAllocationRepository allocationRepository;
    private final CpfWithdrawalPurposeMasterRepository purposeRepository;
    private final CpfWithdrawalRuleService ruleService;
    private final CpfWithdrawalRuleEngine ruleEngine;
    private final CpfHeadMasterRepository headRepository;
    private final EmployeeRepository employeeRepository;
    private final CpfTrustMemberLedgerEntryRepository ledgerRepository;
    private final CpfLoanApplicationService loanApplicationService;
    private final CpfLoanApplicationRepository loanApplicationRepository;
    private final CpfRateResolutionService rateResolutionService;
    private final CpfRuleDocumentRepository ruleDocumentRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final DaRateHistoryRepository daRateHistoryRepository;
    private final ObjectMapper objectMapper;

    public CpfApplicationService(CpfApplicationRepository applicationRepository, CpfApplicationLedgerAllocationRepository allocationRepository,
                                  CpfWithdrawalPurposeMasterRepository purposeRepository, CpfWithdrawalRuleService ruleService,
                                  CpfWithdrawalRuleEngine ruleEngine, CpfHeadMasterRepository headRepository, EmployeeRepository employeeRepository,
                                  CpfTrustMemberLedgerEntryRepository ledgerRepository, CpfLoanApplicationService loanApplicationService,
                                  CpfLoanApplicationRepository loanApplicationRepository, CpfRateResolutionService rateResolutionService,
                                  CpfRuleDocumentRepository ruleDocumentRepository, RegularPayFixationRepository regularPayFixationRepository,
                                  DaRateHistoryRepository daRateHistoryRepository, ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.allocationRepository = allocationRepository;
        this.purposeRepository = purposeRepository;
        this.ruleService = ruleService;
        this.ruleEngine = ruleEngine;
        this.headRepository = headRepository;
        this.employeeRepository = employeeRepository;
        this.ledgerRepository = ledgerRepository;
        this.loanApplicationService = loanApplicationService;
        this.loanApplicationRepository = loanApplicationRepository;
        this.rateResolutionService = rateResolutionService;
        this.ruleDocumentRepository = ruleDocumentRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.daRateHistoryRepository = daRateHistoryRepository;
        this.objectMapper = objectMapper;
    }

    public CpfApplicationEligibilityResponse checkEligibility(String employeeCode, String purposeCode,
                                                               BigDecimal basicPlusDa, BigDecimal propertyCost, BigDecimal payrollDeductionCapacity) {
        return checkEligibility(employeeCode, purposeCode, basicPlusDa, propertyCost, payrollDeductionCapacity, null);
    }

    /**
     * outstandingLoan feeds the OUTSTANDING_LOAN ceiling metric (Part 8/13 of the original spec) - HOUSING_LOAN_REPAYMENT's
     * own configured ceiling is MIN(36*BasicPlusDA, outstanding housing loan, eligible balance), so without this
     * parameter that purpose's ceiling always resolved to MIN(..., 0, ...) = 0, making it unusable. The 5-arg
     * overload above defaults it to null/zero for every OTHER purpose, which never reads OUTSTANDING_LOAN anyway.
     * For HOUSING_LOAN_REPAYMENT specifically, this parameter is ignored in favor of the authoritative
     * server-resolved value (Task 4 - see the 8-arg overload's own javadoc) - kept in the signature only
     * so existing callers (sanction()'s re-validation) don't need to change.
     */
    public CpfApplicationEligibilityResponse checkEligibility(String employeeCode, String purposeCode,
                                                               BigDecimal basicPlusDa, BigDecimal propertyCost, BigDecimal payrollDeductionCapacity,
                                                               BigDecimal outstandingLoan) {
        return checkEligibility(employeeCode, purposeCode, basicPlusDa, propertyCost, payrollDeductionCapacity, outstandingLoan, null, null);
    }

    /**
     * Task 4 (Simulator / Apply for Loan repair): the full-fidelity eligibility check, additionally
     * returning a structured ceiling-component breakdown, the configured head debit-allocation preview,
     * and (for refundable purposes once a tenure is known/resolvable) a non-mutating repayment preview -
     * all computed by the SAME CpfWithdrawalRuleEngine/CpfLoanApplicationService calls the 6-arg overload
     * and sanction()/createLinkedLoan() already use, never a second calculation. requestedAmount and
     * tenureMonths are both optional/preview-only inputs - omitting them still returns eligibility,
     * ceiling, and head-priority-order (just without a requested-amount-specific allocation preview or a
     * repayment preview), matching how a Simulator/wizard would call this before the applicant has
     * committed to a final amount/tenure.
     *
     * <p>For HOUSING_LOAN_REPAYMENT, {@code outstandingLoan} is IGNORED and replaced with the
     * authoritative sum of the employee's own non-CLOSED CPF Trust housing loans (Task 4 Part 12 - "do
     * not ask the employee to enter... retrieve from the existing approved source") - reusing
     * {@link CpfLoanApplicationRepository#findByEmployeeIdOrderByCreatedAtDesc}, not a new balance table.
     *
     * <p>{@code basicPlusDa} is likewise IGNORED for every purpose and replaced with the employee's own
     * current Basic Pay + DA, resolved from their latest DISBURSED {@link PayrollMonthlyRecord} (never
     * asked of the applicant - see {@link #resolveCurrentBasicPlusDa}). Kept in the signature only so
     * existing callers don't need to change.
     */
    public CpfApplicationEligibilityResponse checkEligibility(String employeeCode, String purposeCode,
                                                               BigDecimal basicPlusDa, BigDecimal propertyCost, BigDecimal payrollDeductionCapacity,
                                                               BigDecimal outstandingLoan, BigDecimal requestedAmount, Integer tenureMonths) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new BusinessRuleViolationException("No employee found with code " + employeeCode));
        CpfWithdrawalPurposeMaster purpose = purposeRepository.findByCode(purposeCode)
                .orElseThrow(() -> new BusinessRuleViolationException("Unknown purpose code " + purposeCode));
        CpfWithdrawalRuleVersion version = ruleService.resolveActiveVersion(purposeCode, LocalDate.now())
                .orElseThrow(() -> new BusinessRuleViolationException("No APPROVED, currently-effective withdrawal rule is configured for purpose " + purposeCode));
        CpfWithdrawalRuleDetail detail = ruleService.requireDetail(version);

        BigDecimal effectiveOutstandingLoan = isHousingLoanRepayment(purposeCode)
                ? resolveOutstandingHousingLoan(employee.getId())
                : outstandingLoan;
        BigDecimal effectiveBasicPlusDa = resolveCurrentBasicPlusDa(employee.getId());

        Map<String, BigDecimal> headBalances = resolveHeadBalances(employee.getId());
        var ceiling = ruleEngine.evaluateCeiling(detail, new CpfWithdrawalRuleEngine.CeilingEvaluationContext(
                effectiveBasicPlusDa, headBalances, propertyCost, payrollDeductionCapacity, effectiveOutstandingLoan));
        var serviceEligibility = ruleEngine.evaluateServiceEligibility(detail, employee, LocalDate.now());
        var frequency = ruleEngine.evaluateFrequency(detail, employeeCode, purpose.getId(), LocalDate.now());

        boolean eligible = serviceEligibility.eligible() && frequency.eligible() && ceiling.finalAmount().signum() > 0;
        String reason = !serviceEligibility.eligible() ? serviceEligibility.reason()
                : !frequency.eligible() ? frequency.reason()
                : ceiling.finalAmount().signum() <= 0 ? "No eligible CPF Trust corpus under rule version " + version.getVersionTag()
                : "Eligible for up to " + ceiling.finalAmount() + " under rule version " + version.getVersionTag();

        List<CpfRuleDocumentConfig> requiredDocuments = ruleDocumentRepository.findByDetail_Id(detail.getId()).stream()
                .map(d -> new CpfRuleDocumentConfig(d.getDocumentName(), d.isMandatory(), d.getAllowedMimeTypes(), d.getMaxSizeKb()))
                .toList();
        List<CeilingComponentResponse> ceilingComponents = ceiling.components().stream().map(CeilingComponentResponse::from).toList();

        BigDecimal previewAmount = requestedAmount != null ? requestedAmount.min(ceiling.finalAmount()) : ceiling.finalAmount();
        var allocation = ruleEngine.allocateDebitAcrossHeads(detail, previewAmount.max(BigDecimal.ZERO), headBalances);
        List<HeadAllocationResponse> headAllocation = ruleEngine.resolveEligibleHeadsOrdered(detail).stream()
                .map(h -> new HeadAllocationResponse(h.getHead().getCode(), h.getHead().getName(), h.getDebitPriority(),
                        allocation.debitByHeadCode().getOrDefault(h.getHead().getCode(), BigDecimal.ZERO)))
                .toList();

        RepaymentPreviewResponse repaymentPreview = eligible ? buildRepaymentPreview(purpose, detail, previewAmount, tenureMonths) : null;
        boolean carriesRepaymentSchedule = purpose.getType().isRefundable()
                && detail.getInterestMethod() != null && !detail.getInterestMethod().isBlank();

        return new CpfApplicationEligibilityResponse(purposeCode, version.getId().toString(), version.getVersionTag(), eligible, reason,
                ceiling.totalEligibleBalance(), ceiling.finalAmount(), serviceEligibility.eligible(), serviceEligibility.reason(),
                frequency.eligible(), frequency.reason(), ceiling.trace(), requiredDocuments, ceilingComponents, headAllocation, repaymentPreview,
                carriesRepaymentSchedule,
                carriesRepaymentSchedule ? detail.getMinTenureMonths() : null,
                carriesRepaymentSchedule ? detail.getMaxTenureMonths() : null,
                carriesRepaymentSchedule ? detail.getDefaultTenureMonths() : null);
    }

    /** Preview-only - reuses sanction()/createLinkedLoan()'s own tenure/rate/interest helpers, posts nothing.
     * Returns null (rather than throwing) when this purpose carries no repayment schedule at all, or when a
     * tenure can't yet be resolved (no default configured and none supplied) - both are normal "not ready
     * to preview yet" states, not errors, at the point the Simulator/wizard first shows a purpose. */
    private RepaymentPreviewResponse buildRepaymentPreview(CpfWithdrawalPurposeMaster purpose, CpfWithdrawalRuleDetail detail,
                                                             BigDecimal previewAmount, Integer requestedTenureMonths) {
        boolean carriesRepaymentSchedule = purpose.getType().isRefundable()
                && detail.getInterestMethod() != null && !detail.getInterestMethod().isBlank();
        if (!carriesRepaymentSchedule || previewAmount.signum() <= 0) {
            return null;
        }
        int tenureMonths;
        try {
            tenureMonths = resolveTenure(detail, requestedTenureMonths);
        } catch (BusinessRuleViolationException noTenureYet) {
            return null;
        }
        BigDecimal rate = resolveInterestRate(detail);
        BigDecimal monthlyPrincipal = loanApplicationService.computeMonthlyRecovery(previewAmount, tenureMonths);
        BigDecimal totalInterest = loanApplicationService.computeTotalInterest(previewAmount, tenureMonths, rate);
        int interestInstallments = loanApplicationService.resolveInterestInstallmentsFromPolicy(tenureMonths);
        BigDecimal monthlyInterest = totalInterest.divide(BigDecimal.valueOf(interestInstallments), 2, RoundingMode.HALF_UP);
        return new RepaymentPreviewResponse(tenureMonths, previewAmount, monthlyPrincipal, tenureMonths, interestInstallments,
                tenureMonths + 1, monthlyInterest, totalInterest, previewAmount.add(totalInterest));
    }

    private boolean isHousingLoanRepayment(String purposeCode) {
        return "HOUSING_LOAN_REPAYMENT".equalsIgnoreCase(purposeCode);
    }

    /** Task 4 Part 12 - the authoritative outstanding CPF Trust housing loan balance, summed from the
     * employee's own non-CLOSED housing-purpose CpfLoanApplication rows (the createLinkedLoan() bridge
     * always stamps purpose = the rule-engine's own "HOUSING_LOAN_REPAYMENT" code) - never a second
     * balance table, never a client-supplied number for this purpose. */
    private BigDecimal resolveOutstandingHousingLoan(Long employeeId) {
        return loanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .filter(loan -> loan.getStatus() != CpfLoanApplicationStatus.CLOSED)
                .filter(loan -> isHousingLoanRepayment(loan.getPurpose()))
                .map(CpfLoanApplication::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Task 4 - "current Basic + DA" (the BASIC_PLUS_DA ceiling metric, used by MEDICAL_EMERGENCY,
     * HOUSING_CONSTRUCTION, HOUSING_LOAN_REPAYMENT and TEMPORARY_HARDSHIP) is derived from the employee's
     * live current pay fixation and the current DA rate - never a payroll RUN, and never asked of the
     * applicant - the same "prefer authoritative master data over a client-supplied figure" precedent as
     * {@link #resolveOutstandingHousingLoan}. basic_pay comes from {@code regular_pay_fixations}' single
     * is_current=true row (RegularPayFixationRepository.findByEmployeeIdAndCurrentTrue - the same row
     * PayrollBatchComputationService itself reads for the SAME employee's actual payroll run); the DA
     * percentage comes from {@code da_rate_history}, keyed by that fixation's grade scale's own scale_type
     * (CDA/IDA) and today's date - the exact same lookup/formula (basic + basic*da%/100)
     * PayrollBatchComputationService.resolveDaPercentage()/its own basicPlusDa computation use, so this
     * never drifts from what payroll would actually compute. Unlike payroll's own resolveDaPercentage
     * (which hard-fails when no rate row covers the date, since a payroll run genuinely cannot proceed
     * without one), a missing DA rate here degrades to DA=0 rather than throwing - an eligibility check is
     * read-only/interactive and must never 500 out from incomplete master data. Resolves to ZERO (never
     * null - {@link CpfWithdrawalRuleEngine.CeilingEvaluationContext} already treats a null basicPlusDa as
     * ZERO) when the employee has no current pay fixation at all (e.g. a non-REGULAR employment category),
     * rather than fabricating a wage. */
    private BigDecimal resolveCurrentBasicPlusDa(Long employeeId) {
        return regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employeeId)
                .map(fixation -> {
                    ScaleType scaleType = fixation.getGradeScale().getScaleType();
                    BigDecimal daPercentage = daRateHistoryRepository
                            .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(scaleType, LocalDate.now())
                            .map(DaRateHistory::getDaPercentage)
                            .orElse(BigDecimal.ZERO);
                    BigDecimal da = fixation.getBasicPay().multiply(daPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    return fixation.getBasicPay().add(da);
                })
                .orElse(BigDecimal.ZERO);
    }

    /** Part 29 - every mandatory CpfRuleDocument for this rule detail must have a matching (by documentName) submission before the application may be created. */
    private void requireMandatoryDocuments(CpfWithdrawalRuleDetail detail, List<CpfApplicationDocumentSubmission> submitted) {
        List<CpfRuleDocument> required = ruleDocumentRepository.findByDetail_Id(detail.getId());
        List<String> submittedNames = submitted == null ? List.of() : submitted.stream().map(CpfApplicationDocumentSubmission::documentName).toList();
        List<String> missing = required.stream()
                .filter(CpfRuleDocument::isMandatory)
                .map(CpfRuleDocument::getDocumentName)
                .filter(name -> !submittedNames.contains(name))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleViolationException("Missing mandatory supporting document(s): " + String.join(", ", missing));
        }
    }

    @Transactional
    public CpfApplicationResponse apply(CpfApplicationRequest request, String createdBy) {
        // Idempotency (Task 4 Part 10), short-circuit: a retry (double-click/browser retry/network retry)
        // must return the existing APPLIED application rather than re-running eligibility. This matters
        // because maxActiveConcurrency (evaluateFrequency, below) would otherwise reject the retry with
        // "Maximum concurrent applications already reached" before the save-time
        // DataIntegrityViolationException catch further down is ever reached - for any purpose whose
        // concurrency cap is 1 (every live rule today), that catch is unreachable dead code without this
        // check. The uq_cpf_application_pending_per_purpose index (V89) remains the race-safe backstop for
        // two concurrent requests that both pass this check before either commits.
        Optional<CpfApplication> existingPending = purposeRepository.findByCode(request.purposeCode())
                .flatMap(p -> applicationRepository.findByEmployeeCodeAndPurpose_IdAndStatusIn(request.employeeCode(), p.getId(),
                        List.of(CpfApplicationStatus.APPLIED)).stream().findFirst());
        if (existingPending.isPresent()) {
            return CpfApplicationResponse.from(existingPending.get());
        }

        var eligibility = checkEligibility(request.employeeCode(), request.purposeCode(), request.basicPlusDa(), request.propertyCost(),
                request.payrollDeductionCapacity(), request.outstandingLoan());
        if (!eligibility.serviceEligible()) {
            throw new BusinessRuleViolationException(eligibility.serviceEligibilityReason());
        }
        if (!eligibility.frequencyEligible()) {
            throw new BusinessRuleViolationException(eligibility.frequencyReason());
        }
        if (request.appliedAmount().compareTo(eligibility.eligibleAmount()) > 0) {
            throw new BusinessRuleViolationException("Applied amount " + request.appliedAmount() + " exceeds the maximum eligible amount of "
                    + eligibility.eligibleAmount() + " under rule version " + eligibility.ruleVersionTag());
        }

        CpfWithdrawalPurposeMaster purpose = purposeRepository.findByCode(request.purposeCode())
                .orElseThrow(() -> new BusinessRuleViolationException("Unknown purpose code " + request.purposeCode()));
        CpfWithdrawalRuleVersion version = ruleService.findEntity(UUID.fromString(eligibility.ruleVersionId())).orElseThrow();
        CpfWithdrawalRuleDetail detail = ruleService.requireDetail(version);
        requireMandatoryDocuments(detail, request.submittedDocuments());

        CpfApplication application = new CpfApplication(generateApplicationNumber(), request.employeeCode(), purpose, version,
                request.appliedAmount(), eligibility.eligibleAmount(), toJson(eligibility.calculationTrace()));
        application.setSubmittedDocuments(toJson(request.submittedDocuments() == null ? List.of() : request.submittedDocuments()));

        // Idempotency (Task 4 Part 10): uq_cpf_application_pending_per_purpose (V89) is the real,
        // race-safe backstop against a double-click/browser retry/network retry - on conflict, return the
        // already-existing APPLIED application instead of erroring or creating a duplicate.
        try {
            return CpfApplicationResponse.from(applicationRepository.saveAndFlush(application));
        } catch (DataIntegrityViolationException duplicateSubmit) {
            return applicationRepository.findByEmployeeCodeAndPurpose_IdAndStatusIn(request.employeeCode(), purpose.getId(), List.of(CpfApplicationStatus.APPLIED))
                    .stream().findFirst()
                    .map(CpfApplicationResponse::from)
                    .orElseThrow(() -> duplicateSubmit);
        }
    }

    @Transactional
    public CpfApplicationResponse sanction(UUID applicationId, CpfApplicationSanctionRequest request) {
        CpfApplication application = findOrThrow(applicationId);
        if (application.getStatus() != CpfApplicationStatus.APPLIED) {
            throw new BusinessRuleViolationException("Application " + applicationId + " must be APPLIED to sanction but is " + application.getStatus());
        }
        CpfWithdrawalRuleDetail detail = ruleService.requireDetail(application.getRuleVersion());

        // Re-validate against the live ledger/service facts at sanction time, using re-confirmed context.
        var eligibility = checkEligibility(application.getEmployeeCode(), application.getPurpose().getCode(),
                request.basicPlusDa(), request.propertyCost(), request.payrollDeductionCapacity(), request.outstandingLoan());
        if (request.sanctionedAmount().compareTo(eligibility.eligibleAmount()) > 0) {
            throw new BusinessRuleViolationException("Sanctioned amount " + request.sanctionedAmount() + " exceeds the maximum eligible amount of "
                    + eligibility.eligibleAmount() + " under rule version " + eligibility.ruleVersionTag());
        }

        // interest_method/default_tenure_months are populated on every live seed row uniformly (including
        // non-refundable withdrawals like MEDICAL_EMERGENCY, where a tenure/EMI makes no business sense) -
        // this is a data-seeding artifact, not something to "fix" by editing the seed data (Part 2: never
        // modify an already-approved rule). The correct signal for "does this application actually carry a
        // repayment schedule" is the withdrawal TYPE's own is_refundable flag, not merely whether
        // interest_method happens to be non-null.
        boolean carriesRepaymentSchedule = application.getPurpose().getType().isRefundable()
                && detail.getInterestMethod() != null && !detail.getInterestMethod().isBlank();

        Integer tenureMonths = null;
        BigDecimal emi = null;
        if (carriesRepaymentSchedule) {
            tenureMonths = resolveTenure(detail, request.tenureMonths());
            BigDecimal rate = resolveInterestRate(detail);
            loanApplicationService.computeTotalInterest(request.sanctionedAmount(), tenureMonths, rate); // validated reuse of the existing formula; totalInterest itself isn't tracked on cpf_application (no column for it)
            emi = request.sanctionedAmount().divide(BigDecimal.valueOf(tenureMonths), 2, RoundingMode.HALF_UP);
        }

        application.setSanctionedAmount(request.sanctionedAmount());
        application.setTenureMonths(tenureMonths);
        application.setCalculatedEmi(emi);
        application.setSanctionedAt(Instant.now());
        application.setStatus(CpfApplicationStatus.SANCTIONED);
        return CpfApplicationResponse.from(application);
    }

    /**
     * Debits per {@link CpfWithdrawalRuleEngine#allocateDebitAcrossHeads} (Part 9): persists a
     * {@link CpfApplicationLedgerAllocation} row per head (this flow's own breakdown/audit view) AND posts
     * a LOAN_WITHDRAWAL entry to {@code cpf_trust_member_ledger_entries} (the one authoritative
     * running-balance ledger) - never a second, competing balance engine (Part 34). For a REFUNDABLE
     * purpose this also bridges into a linked {@link in.gov.jci.hrms.entity.CpfLoanApplication} (Part 4)
     * so the pre-existing repayment/payroll-recovery/cash-settlement machinery activates - see
     * {@link #createLinkedLoan}.
     *
     * <h2>Idempotency (Part 6)</h2>
     * A retry against an already-DISBURSED application returns the original result (including its
     * already-linked loan, if any) rather than re-validating SANCTIONED and throwing, and never
     * re-posts the ledger withdrawal or creates a second loan.
     *
     * <h2>Atomicity (Part 5)</h2>
     * Every step below - ledger allocation rows, the ledger withdrawal, and the linked loan - happens
     * inside this one @Transactional method; a failure at any point (e.g. loan creation) rolls back the
     * ledger posting and allocation rows with it, via the same transaction, so a withdrawal is never
     * left posted without its loan (or vice versa).
     */
    @Transactional
    public CpfApplicationResponse disburse(UUID applicationId) {
        CpfApplication application = findOrThrow(applicationId);
        if (application.getStatus() == CpfApplicationStatus.DISBURSED) {
            return CpfApplicationResponse.from(application,
                    loanApplicationRepository.findByCpfApplicationId(application.getId()).map(CpfLoanApplication::getId).orElse(null));
        }
        if (application.getStatus() != CpfApplicationStatus.SANCTIONED) {
            throw new BusinessRuleViolationException("Application " + applicationId + " must be SANCTIONED to disburse but is " + application.getStatus());
        }
        CpfWithdrawalRuleDetail detail = ruleService.requireDetail(application.getRuleVersion());
        Employee employee = employeeRepository.findByEmployeeCode(application.getEmployeeCode())
                .orElseThrow(() -> new BusinessRuleViolationException("No employee found with code " + application.getEmployeeCode()));

        Map<String, BigDecimal> headBalances = resolveHeadBalances(employee.getId());
        var allocation = ruleEngine.allocateDebitAcrossHeads(detail, application.getSanctionedAmount(), headBalances);

        Map<String, CpfHeadMaster> headsByCode = new LinkedHashMap<>();
        for (String code : allocation.debitByHeadCode().keySet()) {
            headRepository.findByCode(code).ifPresent(h -> headsByCode.put(code, h));
        }
        for (var entry : allocation.debitByHeadCode().entrySet()) {
            if (entry.getValue().signum() > 0) {
                CpfHeadMaster head = headsByCode.get(entry.getKey());
                if (head != null) {
                    allocationRepository.save(new CpfApplicationLedgerAllocation(application, head, entry.getValue()));
                }
            }
        }

        postLedgerWithdrawal(employee, application, allocation.debitByHeadCode());

        Instant disbursedAt = Instant.now();
        application.setStatus(CpfApplicationStatus.DISBURSED);
        application.setDisbursedAt(disbursedAt);

        Long linkedLoanId = null;
        if (application.getPurpose().getType().isRefundable()) {
            linkedLoanId = createLinkedLoan(application, detail, employee, disbursedAt).getId();
        }
        return CpfApplicationResponse.from(application, linkedLoanId);
    }

    /**
     * Bridges a refundable rule-engine disbursement into the pre-existing CpfLoanApplication
     * repayment/recovery/settlement machinery (Part 1 architecture decision) - deliberately does NOT
     * call CpfLoanApplicationService.applyLoan()/sanctionLoan()/disburseLoan(), since each of those
     * re-validates eligibility against that service's own hardcoded 75%-of-EE+VPF cap (already
     * superseded here by the rule engine's own ceiling) and disburseLoan() would post a SECOND
     * LOAN_WITHDRAWAL ledger entry for money already debited by postLedgerWithdrawal() above (Part 9:
     * no second, competing balance engine). Instead this constructs the loan directly, already in its
     * DISBURSED terminal-correct state, reusing only the two pure calculation helpers
     * (computeTotalInterest/computeMonthlyRecovery/resolveInterestInstallmentsFromPolicy) that don't
     * mutate anything or post to the ledger a second time.
     */
    private CpfLoanApplication createLinkedLoan(CpfApplication application, CpfWithdrawalRuleDetail detail, Employee employee, Instant disbursedAt) {
        Integer tenureMonths = application.getTenureMonths();
        if (tenureMonths == null) {
            throw new BusinessRuleViolationException("Purpose " + application.getPurpose().getCode()
                    + " is refundable but its rule detail carries no interest_method/tenure - cannot create a repayment schedule for a loan without one.");
        }

        CpfLoanApplication loan = new CpfLoanApplication(loanApplicationService.generateLoanApplicationNo(), employee,
                CpfLoanType.REFUNDABLE_LOAN, application.getPurpose().getCode(), application.getAppliedAmount());
        loan.setCpfApplicationId(application.getId());
        loan.setSanctionOrderNo(application.getApplicationNumber());
        loan.setSanctionDate(application.getSanctionedAt() != null
                ? application.getSanctionedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate() : LocalDate.now());
        loan.setSanctionedAmount(application.getSanctionedAmount());
        loan.setTotalInstallments(tenureMonths);
        loan.setMonthlyRecoveryPrincipal(loanApplicationService.computeMonthlyRecovery(application.getSanctionedAmount(), tenureMonths));
        loan.setOutstandingBalance(application.getSanctionedAmount());
        loan.setRecoveryPhase(CpfLoanRecoveryPhase.PRINCIPAL);

        BigDecimal effectiveRate = resolveInterestRate(detail);
        BigDecimal totalInterest = loanApplicationService.computeTotalInterest(application.getSanctionedAmount(), tenureMonths, effectiveRate);
        int interestInstallments = loanApplicationService.resolveInterestInstallmentsFromPolicy(tenureMonths);
        loan.setInterestRate(effectiveRate);
        loan.setTotalInterestAmount(totalInterest);
        loan.setOutstandingInterest(totalInterest);
        loan.setTotalInterestInstallments(interestInstallments);
        loan.setMonthlyRecoveryInterest(totalInterest.divide(BigDecimal.valueOf(interestInstallments), 2, RoundingMode.HALF_UP));

        loan.setStatus(CpfLoanApplicationStatus.DISBURSED);
        loan.setDisbursedAt(disbursedAt);
        return loanApplicationRepository.saveAndFlush(loan);
    }

    @Transactional
    public CpfApplicationResponse reject(UUID applicationId) {
        CpfApplication application = findOrThrow(applicationId);
        if (application.getStatus() != CpfApplicationStatus.APPLIED) {
            throw new BusinessRuleViolationException("Application " + applicationId + " must be APPLIED to reject but is " + application.getStatus());
        }
        application.setStatus(CpfApplicationStatus.REJECTED);
        return CpfApplicationResponse.from(application);
    }

    public List<CpfApplicationResponse> findByEmployee(String employeeCode) {
        return applicationRepository.findByEmployeeCodeOrderByCreatedAtDesc(employeeCode).stream().map(CpfApplicationResponse::from).toList();
    }

    private void postLedgerWithdrawal(Employee employee, CpfApplication application, Map<String, BigDecimal> debitByHeadCode) {
        BigDecimal eeShareDebit = debitByHeadCode.getOrDefault("HEAD_A", BigDecimal.ZERO);
        BigDecimal vpfDebit = debitByHeadCode.getOrDefault("HEAD_B", BigDecimal.ZERO);
        BigDecimal erShareDebit = debitByHeadCode.getOrDefault("HEAD_C", BigDecimal.ZERO);

        BigDecimal priorEe = BigDecimal.ZERO, priorEr = BigDecimal.ZERO, priorVpf = BigDecimal.ZERO;
        var priorEntry = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employee.getId());
        if (priorEntry.isPresent()) {
            priorEe = priorEntry.get().getRunningEeBalance();
            priorEr = priorEntry.get().getRunningErBalance();
            priorVpf = priorEntry.get().getRunningVpfBalance();
        }

        LocalDate valueDate = LocalDate.now();
        String finYear = IncomingFundTransferService.financialYearFor(valueDate);
        BigDecimal newEe = priorEe.subtract(eeShareDebit);
        BigDecimal newEr = priorEr.subtract(erShareDebit);
        BigDecimal newVpf = priorVpf.subtract(vpfDebit);
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate, CpfLedgerEntryType.LOAN_WITHDRAWAL,
                newEe, newEr, newVpf, newEe.add(newEr).add(newVpf));
        entry.setEeShareDebit(eeShareDebit);
        entry.setErShareDebit(erShareDebit);
        entry.setVpfDebit(vpfDebit);
        entry.setTotalDebit(eeShareDebit.add(erShareDebit).add(vpfDebit));
        entry.setReferenceDocNo(application.getApplicationNumber());
        entry.setRemarks("CPF withdrawal disbursed against rule-driven application " + application.getApplicationNumber()
                + " (rule " + application.getRuleVersion().getVersionTag() + ")");
        ledgerRepository.save(entry);
    }

    private Map<String, BigDecimal> resolveHeadBalances(Long employeeId) {
        var latest = ledgerRepository.findFirstByEmployee_IdOrderByValueDateDescIdDesc(employeeId);
        BigDecimal ee = latest.map(CpfTrustMemberLedgerEntry::getRunningEeBalance).orElse(BigDecimal.ZERO);
        BigDecimal vpf = latest.map(CpfTrustMemberLedgerEntry::getRunningVpfBalance).orElse(BigDecimal.ZERO);
        BigDecimal er = latest.map(CpfTrustMemberLedgerEntry::getRunningErBalance).orElse(BigDecimal.ZERO);
        return Map.of("HEAD_A", ee, "HEAD_B", vpf, "HEAD_C", er);
    }

    private Integer resolveTenure(CpfWithdrawalRuleDetail detail, Integer requested) {
        Integer tenure = requested != null ? requested : detail.getDefaultTenureMonths();
        if (tenure == null) {
            throw new BusinessRuleViolationException("Rule requires interest but no tenure could be determined (no default_tenure_months configured and none supplied).");
        }
        if (detail.getMinTenureMonths() != null && tenure < detail.getMinTenureMonths()) {
            throw new BusinessRuleViolationException("Tenure " + tenure + " months is below the rule's minimum of " + detail.getMinTenureMonths() + " months.");
        }
        if (detail.getMaxTenureMonths() != null && tenure > detail.getMaxTenureMonths()) {
            throw new BusinessRuleViolationException("Tenure " + tenure + " months exceeds the rule's maximum of " + detail.getMaxTenureMonths() + " months.");
        }
        return tenure;
    }

    private BigDecimal resolveInterestRate(CpfWithdrawalRuleDetail detail) {
        if (detail.getInterestRateAnnual() != null && detail.getInterestRateAnnual().signum() > 0) {
            return detail.getInterestRateAnnual();
        }
        String finYear = IncomingFundTransferService.financialYearFor(LocalDate.now());
        return rateResolutionService.resolveStatutoryRate(finYear).loanRate();
    }

    private CpfApplication findOrThrow(UUID id) {
        return applicationRepository.findById(id).orElseThrow(() -> new BusinessRuleViolationException("CPF application " + id + " not found"));
    }

    /** "CPFA/{finYear}/{seq, 4 digits}" - same low-volume, human-paced-workflow rationale as IncomingFundTransferService's own voucher generator. */
    private String generateApplicationNumber() {
        String finYear = IncomingFundTransferService.financialYearFor(LocalDate.now());
        String prefix = "CPFA/" + finYear + "/";
        long seq = applicationRepository.countByApplicationNumberStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
