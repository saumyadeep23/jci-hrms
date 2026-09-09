package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.SubsistenceReviewRequest;
import in.gov.jci.hrms.dto.SuspensionInitiateRequest;
import in.gov.jci.hrms.dto.SuspensionRevocationRequest;
import in.gov.jci.hrms.dto.SuspensionRevocationResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeSuspensionNec;
import in.gov.jci.hrms.entity.EmployeeSuspensionRecord;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.SuspensionStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository;
import in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Suspension lifecycle: initiateSuspension -&gt; (monthly) submitAndVerifyNec -&gt; (at 90 days)
 * reviewSubsistenceAllowance -&gt; revokeAndRegularize. PayrollBatchComputationService's own suspension
 * hook is the actual payroll-side consumer of this state (NEC gate, Head 66, CPF/NPS suppression) - see
 * its own javadoc.
 */
@Service
@Transactional(readOnly = true)
public class SuspensionLifecycleService {

    /** payroll_salary_heads.head_count 66 - Subsistence Allowance during Suspension (SUBSIST_ALLOW). */
    private static final int HEAD_SUBSIST_ALLOW = 66;
    private static final BigDecimal CPF_RATE_ON_ARREAR = new BigDecimal("12.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MIN_DAYS_BEFORE_REVIEW = 90;

    private final EmployeeSuspensionRecordRepository suspensionRepository;
    private final EmployeeSuspensionNecRepository necRepository;
    private final EmployeeRepository employeeRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;
    private final PayrollComputationService payrollComputationService;
    private final PayrollMonthlyHeadItemRepository headItemRepository;

    public SuspensionLifecycleService(EmployeeSuspensionRecordRepository suspensionRepository,
                                       EmployeeSuspensionNecRepository necRepository,
                                       EmployeeRepository employeeRepository,
                                       RegularPayFixationRepository regularPayFixationRepository,
                                       PayrollComputationService payrollComputationService,
                                       PayrollMonthlyHeadItemRepository headItemRepository) {
        this.suspensionRepository = suspensionRepository;
        this.necRepository = necRepository;
        this.employeeRepository = employeeRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
        this.payrollComputationService = payrollComputationService;
        this.headItemRepository = headItemRepository;
    }

    @Transactional
    public EmployeeSuspensionRecord initiateSuspension(SuspensionInitiateRequest request) {
        Employee employee = resolveEmployee(request.employeeId());
        if (suspensionRepository.findByEmployee_IdAndStatus(employee.getId(), SuspensionStatus.UNDER_SUSPENSION).isPresent()) {
            throw new BusinessRuleViolationException("Employee " + employee.getId() + " already has an active suspension");
        }
        EmployeeSuspensionRecord record = new EmployeeSuspensionRecord(employee, request.suspensionOrderNo(),
                request.suspensionOrderDate(), request.effectiveFrom(), request.hqStation(), null);
        employee.setStatus(EmployeeStatus.SUSPENDED);
        return suspensionRepository.save(record);
    }

    /** find-or-create the month's NEC row, then mark it submitted+verified in one step (the "Record NEC" action). */
    @Transactional
    public EmployeeSuspensionNec submitAndVerifyNec(Long suspensionId, int salMonth, int salYear, String docReference, Long verifierId) {
        EmployeeSuspensionRecord suspension = findSuspensionOrThrow(suspensionId);
        if (suspension.getStatus() != SuspensionStatus.UNDER_SUSPENSION) {
            throw new BusinessRuleViolationException("Suspension " + suspensionId + " is not currently active (is " + suspension.getStatus() + ")");
        }
        EmployeeSuspensionNec nec = necRepository.findBySuspension_IdAndSalMonthAndSalYear(suspensionId, salMonth, salYear)
                .orElseGet(() -> new EmployeeSuspensionNec(suspension, suspension.getEmployee(), salMonth, salYear));
        nec.setNecSubmitted(true);
        nec.setSubmissionDate(LocalDate.now());
        nec.setDocReference(docReference);
        nec.setVerified(true);
        nec.setVerifiedBy(resolveEmployee(verifierId));
        nec.setVerifiedAt(Instant.now());
        return necRepository.save(nec);
    }

    @Transactional
    public EmployeeSuspensionRecord reviewSubsistenceAllowance(Long suspensionId, SubsistenceReviewRequest request) {
        EmployeeSuspensionRecord suspension = findSuspensionOrThrow(suspensionId);
        if (suspension.getStatus() != SuspensionStatus.UNDER_SUSPENSION) {
            throw new BusinessRuleViolationException("Suspension " + suspensionId + " is not currently active (is " + suspension.getStatus() + ")");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(suspension.getEffectiveFrom(), LocalDate.now()) < MIN_DAYS_BEFORE_REVIEW) {
            throw new BusinessRuleViolationException(
                    "Subsistence review is not due yet - fewer than " + MIN_DAYS_BEFORE_REVIEW + " days have elapsed since " + suspension.getEffectiveFrom());
        }
        BigDecimal newPercentage = request.delayAttributableToEmployee() ? new BigDecimal("25.00") : new BigDecimal("75.00");
        suspension.setCurrentSubsistencePercentage(newPercentage);
        suspension.setReviewDate(LocalDate.now());
        suspension.setReviewOrderNo(request.reviewOrderNo());
        suspension.setReviewRemarks(request.reviewRemarks());
        return suspensionRepository.save(suspension);
    }

    /**
     * Closes the suspension and computes (but does not itself disburse - see SuspensionRevocationResponse's
     * own javadoc) the back-pay arrear: full pay (current Basic+DA, applied uniformly across the whole
     * suspension window - a simplification, since this schema keeps no month-by-month subsistence-rate
     * history to reconstruct exactly what varied) minus subsistence actually paid (summed from real Head
     * 66 payroll history), less a flat 12% CPF deduction on the resulting gross arrear.
     */
    @Transactional
    public SuspensionRevocationResponse revokeAndRegularize(Long suspensionId, SuspensionRevocationRequest request) {
        EmployeeSuspensionRecord suspension = findSuspensionOrThrow(suspensionId);
        if (suspension.getStatus() != SuspensionStatus.UNDER_SUSPENSION) {
            throw new BusinessRuleViolationException("Suspension " + suspensionId + " is not currently active (is " + suspension.getStatus() + ")");
        }
        Employee employee = suspension.getEmployee();
        suspension.setStatus(SuspensionStatus.REVOKED);
        suspension.setRevocationOrderNo(request.revocationOrderNo());
        suspension.setRevocationOrderDate(request.revocationOrderDate());
        suspension.setRevocationEffectiveDate(request.revocationEffectiveDate());
        suspension.setRegularizationType(request.regularizationType());
        suspension.setRegularizationRemarks(request.regularizationRemarks());
        suspensionRepository.save(suspension);

        if (employee.getStatus() == EmployeeStatus.SUSPENDED) {
            employee.setStatus(EmployeeStatus.ACTIVE);
        }

        BigDecimal grossArrear = computeBackPayArrear(employee, suspension.getEffectiveFrom(), request.revocationEffectiveDate());
        BigDecimal cpfOnArrear = round(grossArrear.multiply(CPF_RATE_ON_ARREAR).divide(HUNDRED, 10, RoundingMode.HALF_UP));
        BigDecimal netArrear = grossArrear.subtract(cpfOnArrear);
        return new SuspensionRevocationResponse(suspensionId, grossArrear, cpfOnArrear, netArrear);
    }

    private BigDecimal computeBackPayArrear(Employee employee, LocalDate effectiveFrom, LocalDate revocationEffectiveDate) {
        RegularPayFixation fixation = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId()).orElse(null);
        if (fixation == null || !revocationEffectiveDate.isAfter(effectiveFrom.minusDays(1))) {
            return BigDecimal.ZERO;
        }
        BigDecimal daPercentage = payrollComputationService.resolveDaPercentage(fixation.getGradeScale().getScaleType(), revocationEffectiveDate);
        BigDecimal monthlyFullPay = round(fixation.getBasicPay().add(
                fixation.getBasicPay().multiply(daPercentage).divide(HUNDRED, 10, RoundingMode.HALF_UP)));

        int fromMonth = effectiveFrom.getMonthValue();
        int fromYear = effectiveFrom.getYear();
        int toMonth = revocationEffectiveDate.getMonthValue();
        int toYear = revocationEffectiveDate.getYear();
        int monthsUnderSuspension = (toYear * 12 + toMonth) - (fromYear * 12 + fromMonth) + 1;

        BigDecimal totalFullPay = monthlyFullPay.multiply(BigDecimal.valueOf(Math.max(monthsUnderSuspension, 0)));
        BigDecimal totalSubsistencePaid = headItemRepository.sumAmountForEmployeeAndHeadInRange(
                employee.getId(), HEAD_SUBSIST_ALLOW, fromMonth, fromYear, toMonth, toYear);
        BigDecimal grossArrear = totalFullPay.subtract(totalSubsistencePaid != null ? totalSubsistencePaid : BigDecimal.ZERO);
        return grossArrear.signum() > 0 ? grossArrear : BigDecimal.ZERO;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private EmployeeSuspensionRecord findSuspensionOrThrow(Long id) {
        return suspensionRepository.findById(id).orElseThrow(() -> new MasterDataNotFoundException("Suspension Record", id));
    }

    private Employee resolveEmployee(Long id) {
        if (id == null) {
            return null;
        }
        return employeeRepository.findById(id).orElseThrow(() -> new EmployeeNotFoundException(id));
    }
}
