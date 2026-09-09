package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CeaClaimBillPassRequest;
import in.gov.jci.hrms.dto.CeaClaimResponse;
import in.gov.jci.hrms.dto.CeaClaimSubmitRequest;
import in.gov.jci.hrms.dto.CeaClaimVerifyRequest;
import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.CeaClaimType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeCeaClaim;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Children Education Allowance / Hostel Subsidy claim lifecycle: SUBMITTED -&gt; VERIFIED ->
 * BILL_PASSED, at which point PayrollBatchComputationService.processBatch() picks it up on its next
 * run and moves it to DISBURSED (see EmployeeCeaClaimRepository.findPendingPayrollDisbursement()).
 * Rates are sourced from payroll_statutory_parameters (CEA_STANDARD_RATE/CEA_DIVYANG_RATE/
 * CEA_HOSTEL_SUBSIDY_RATE, seeded in V72), falling back to the same hardcoded values if a row is ever
 * removed - same pattern as PayrollBatchComputationService's own GIS/LWF/EPS rates.
 */
@Service
@Transactional(readOnly = true)
public class CeaClaimService {

    private static final String ENTITY_NAME = "CEA Claim";
    private static final int MAX_CHILDREN_PER_ACADEMIC_YEAR = 2;
    private static final int STANDARD_MAX_AGE = 20;
    private static final int DIVYANG_MAX_AGE = 22;
    private static final BigDecimal DEFAULT_CEA_STANDARD_RATE = new BigDecimal("2812.50");
    private static final BigDecimal DEFAULT_CEA_DIVYANG_RATE = new BigDecimal("5625.00");
    private static final BigDecimal DEFAULT_CEA_HOSTEL_SUBSIDY_RATE = new BigDecimal("8437.50");

    private final EmployeeCeaClaimRepository claimRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDependentRepository dependentRepository;
    private final PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;

    public CeaClaimService(EmployeeCeaClaimRepository claimRepository, EmployeeRepository employeeRepository,
                            EmployeeDependentRepository dependentRepository,
                            PayrollStatutoryParameterRepository payrollStatutoryParameterRepository) {
        this.claimRepository = claimRepository;
        this.employeeRepository = employeeRepository;
        this.dependentRepository = dependentRepository;
        this.payrollStatutoryParameterRepository = payrollStatutoryParameterRepository;
    }

    @Transactional
    public CeaClaimResponse submitClaim(Long employeeId, CeaClaimSubmitRequest request) {
        Employee employee = resolveEmployee(employeeId);
        EmployeeDependent dependent = resolveDependent(employeeId, request.dependentId());

        if (claimRepository.existsByClaimNo(request.claimNo())) {
            throw new MasterDataConflictException("A CEA claim with claim number " + request.claimNo() + " already exists.");
        }
        if (dependent.getRelationship() != FamilyRelationshipType.SON && dependent.getRelationship() != FamilyRelationshipType.DAUGHTER) {
            throw new BusinessRuleViolationException("CEA/Hostel Subsidy claims may only be submitted for a SON or DAUGHTER dependent");
        }
        if (!dependent.isDependent()) {
            throw new BusinessRuleViolationException("Dependent " + dependent.getId() + " is not currently marked as a dependent");
        }
        if (dependent.getDateOfBirth() == null) {
            throw new BusinessRuleViolationException("The dependent's date of birth is required to determine CEA age eligibility");
        }
        if (request.periodTo().isBefore(request.periodFrom())) {
            throw new BusinessRuleViolationException("periodTo must not be before periodFrom");
        }

        int age = Period.between(dependent.getDateOfBirth(), request.periodTo()).getYears();
        int maxAge = dependent.isDivyang() ? DIVYANG_MAX_AGE : STANDARD_MAX_AGE;
        if (age > maxAge) {
            throw new BusinessRuleViolationException("Dependent's age (" + age + ") as of " + request.periodTo()
                    + " exceeds the CEA eligibility limit of " + maxAge + " years" + (dependent.isDivyang() ? " (Divyang)" : ""));
        }

        enforceTwoChildRule(employeeId, dependent, request.academicYear());

        BigDecimal admissibleAmount = computeAdmissibleAmount(request.claimType(), dependent.isDivyang(),
                request.periodFrom(), request.periodTo(), request.claimedAmount());

        EmployeeCeaClaim claim = new EmployeeCeaClaim(request.claimNo(), employee, dependent, request.academicYear(),
                request.claimType(), request.schoolName(), request.standardClass(), request.periodFrom(), request.periodTo(),
                request.claimedAmount(), admissibleAmount);
        claim.setSchoolRegNo(request.schoolRegNo());
        claim.setSupportingDocRef(request.supportingDocRef());
        return CeaClaimResponse.from(claimRepository.saveAndFlush(claim));
    }

    /**
     * At most 2 distinct children per academic year, regardless of how many separate claims each
     * files - a second claim (e.g. CEA and HOSTEL_SUBSIDY, or a resubmission) for a child already
     * counted this year never consumes another slot. A 3rd+ distinct child is only allowed when THAT
     * child's own isMultipleBirthSecondDelivery flag is set - deliberately relying solely on that
     * explicit flag (added for exactly this purpose) rather than also heuristically matching DOBs
     * across dependents, which risks both false positives (unrelated children who happen to share a
     * recorded DOB) and false negatives (genuine multiples whose DOB was entered a day apart).
     */
    private void enforceTwoChildRule(Long employeeId, EmployeeDependent dependent, String academicYear) {
        List<EmployeeCeaClaim> existing = claimRepository.findByEmployeeIdAndAcademicYearAndClaimStatusNot(
                employeeId, academicYear, CeaClaimStatus.REJECTED);
        Set<Long> distinctChildren = existing.stream()
                .map(c -> c.getDependent().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinctChildren.contains(dependent.getId())) {
            return;
        }
        if (distinctChildren.size() >= MAX_CHILDREN_PER_ACADEMIC_YEAR && !dependent.isMultipleBirthSecondDelivery()) {
            throw new BusinessRuleViolationException("CEA/Hostel Subsidy is admissible for at most " + MAX_CHILDREN_PER_ACADEMIC_YEAR
                    + " children per academic year (" + academicYear + ") - mark the dependent as a multiple-birth delivery to claim beyond the limit");
        }
    }

    private BigDecimal computeAdmissibleAmount(CeaClaimType claimType, boolean divyang, LocalDate periodFrom, LocalDate periodTo,
                                                BigDecimal claimedAmount) {
        BigDecimal monthlyRate = resolveMonthlyRate(claimType, divyang);
        long totalMonths = ChronoUnit.MONTHS.between(YearMonth.from(periodFrom), YearMonth.from(periodTo)) + 1;
        BigDecimal cap = monthlyRate.multiply(BigDecimal.valueOf(totalMonths));
        return claimedAmount.min(cap);
    }

    private BigDecimal resolveMonthlyRate(CeaClaimType claimType, boolean divyang) {
        if (claimType == CeaClaimType.HOSTEL_SUBSIDY) {
            return statutoryRate("CEA_HOSTEL_SUBSIDY_RATE", DEFAULT_CEA_HOSTEL_SUBSIDY_RATE);
        }
        return divyang ? statutoryRate("CEA_DIVYANG_RATE", DEFAULT_CEA_DIVYANG_RATE) : statutoryRate("CEA_STANDARD_RATE", DEFAULT_CEA_STANDARD_RATE);
    }

    private BigDecimal statutoryRate(String paramKey, BigDecimal fallback) {
        return payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull(paramKey)
                .map(PayrollStatutoryParameter::getParamValue)
                .orElse(fallback);
    }

    @Transactional
    public CeaClaimResponse verifyClaim(Long claimId, Long verifierId, CeaClaimVerifyRequest request) {
        EmployeeCeaClaim claim = findOrThrow(claimId);
        if (claim.getClaimStatus() != CeaClaimStatus.SUBMITTED) {
            throw new BusinessRuleViolationException("CEA claim " + claimId + " must be SUBMITTED to verify but is " + claim.getClaimStatus());
        }
        if (request.adjustedAdmissibleAmount() != null) {
            claim.setAdmissibleAmount(request.adjustedAdmissibleAmount());
        }
        claim.setClaimStatus(CeaClaimStatus.VERIFIED);
        claim.setVerifiedByOfficer(resolveEmployee(verifierId));
        claim.setVerifiedAt(Instant.now());
        return CeaClaimResponse.from(claim);
    }

    /**
     * Sets claimStatus = BILL_PASSED and records the sanction/bill particulars - this is the terminal
     * manual step; the claim then sits ready for PayrollBatchComputationService.processBatch() to pick
     * up via findPendingPayrollDisbursement() and move to DISBURSED on its own next run.
     */
    @Transactional
    public CeaClaimResponse passBill(Long claimId, Long accountsOfficerId, CeaClaimBillPassRequest request) {
        EmployeeCeaClaim claim = findOrThrow(claimId);
        if (claim.getClaimStatus() != CeaClaimStatus.VERIFIED) {
            throw new BusinessRuleViolationException("CEA claim " + claimId + " must be VERIFIED to pass bill but is " + claim.getClaimStatus());
        }
        claim.setClaimStatus(CeaClaimStatus.BILL_PASSED);
        claim.setPassedAmount(request.passedAmount());
        claim.setBillNo(request.billNo());
        claim.setBillDate(request.billDate());
        claim.setSanctionOrderNo(request.sanctionOrderNo());
        claim.setSanctionDate(request.sanctionDate());
        claim.setPassedByOfficer(resolveEmployee(accountsOfficerId));
        claim.setPassedAt(Instant.now());
        return CeaClaimResponse.from(claim);
    }

    @Transactional
    public CeaClaimResponse rejectClaim(Long claimId, String reason) {
        EmployeeCeaClaim claim = findOrThrow(claimId);
        if (claim.getClaimStatus() != CeaClaimStatus.SUBMITTED && claim.getClaimStatus() != CeaClaimStatus.VERIFIED) {
            throw new BusinessRuleViolationException("CEA claim " + claimId + " can only be rejected while SUBMITTED or VERIFIED (is " + claim.getClaimStatus() + ")");
        }
        claim.setClaimStatus(CeaClaimStatus.REJECTED);
        claim.setRejectionReason(reason);
        return CeaClaimResponse.from(claim);
    }

    public CeaClaimResponse getById(Long claimId) {
        return CeaClaimResponse.from(findOrThrow(claimId));
    }

    public List<CeaClaimResponse> listByEmployee(Long employeeId) {
        resolveEmployee(employeeId);
        return claimRepository.findByEmployeeId(employeeId).stream()
                .map(CeaClaimResponse::from)
                .toList();
    }

    /** HR's review queue - claims awaiting verifyClaim(). */
    public List<CeaClaimResponse> pendingVerification() {
        return claimRepository.findByClaimStatusOrderByCreatedAtAsc(CeaClaimStatus.SUBMITTED).stream()
                .map(CeaClaimResponse::from)
                .toList();
    }

    /** Finance's review queue - claims awaiting passBill(). */
    public List<CeaClaimResponse> pendingBillPassing() {
        return claimRepository.findByClaimStatusOrderByCreatedAtAsc(CeaClaimStatus.VERIFIED).stream()
                .map(CeaClaimResponse::from)
                .toList();
    }

    /** Officers' History / Log view - every claim across every employee, regardless of status, most recent first. */
    public List<CeaClaimResponse> listAll() {
        return claimRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(CeaClaimResponse::from)
                .toList();
    }

    private EmployeeDependent resolveDependent(Long employeeId, Long dependentId) {
        EmployeeDependent dependent = dependentRepository.findById(dependentId)
                .orElseThrow(() -> new MasterDataNotFoundException("Dependent", dependentId));
        if (!dependent.getEmployee().getId().equals(employeeId)) {
            throw new MasterDataNotFoundException("Dependent", dependentId);
        }
        return dependent;
    }

    private EmployeeCeaClaim findOrThrow(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }

    private Employee resolveEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }
}
