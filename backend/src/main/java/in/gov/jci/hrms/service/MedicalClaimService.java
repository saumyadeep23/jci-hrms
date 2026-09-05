package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.MedicalClaimItemRequest;
import in.gov.jci.hrms.dto.MedicalClaimItemResponse;
import in.gov.jci.hrms.dto.MedicalClaimRequest;
import in.gov.jci.hrms.dto.MedicalClaimResponse;
import in.gov.jci.hrms.dto.MedicalClaimVerifyRequest;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.MedicalClaim;
import in.gov.jci.hrms.entity.MedicalClaimItem;
import in.gov.jci.hrms.entity.ReimbursementClaimStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.MedicalClaimItemRepository;
import in.gov.jci.hrms.repository.MedicalClaimRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 4-eyes lifecycle: Draft -> Submit -> HR Verify (per-item allowed_amount
 * adjustment) -> Finance Approve, with Reject available at the Submitted or
 * Verified-by-HR checkpoints.
 *
 * Eligibility ("Regular only, unless contract override flag") is grounded
 * in EmployeeStatus == ACTIVE rather than a true employment-type field -
 * there is no Regular/Contract/Temporary classification anywhere on
 * Employee or in this migration. contractOverride is an explicit,
 * caller-supplied escape hatch rather than something read from persisted
 * data, since there's nothing to read it from yet.
 */
@Service
@Transactional(readOnly = true)
public class MedicalClaimService {

    private static final String ENTITY_NAME = "Medical Claim";

    private final MedicalClaimRepository medicalClaimRepository;
    private final MedicalClaimItemRepository medicalClaimItemRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeDependentRepository employeeDependentRepository;

    public MedicalClaimService(MedicalClaimRepository medicalClaimRepository,
                                MedicalClaimItemRepository medicalClaimItemRepository,
                                EmployeeRepository employeeRepository,
                                EmployeeDependentRepository employeeDependentRepository) {
        this.medicalClaimRepository = medicalClaimRepository;
        this.medicalClaimItemRepository = medicalClaimItemRepository;
        this.employeeRepository = employeeRepository;
        this.employeeDependentRepository = employeeDependentRepository;
    }

    @Transactional
    public MedicalClaimResponse create(MedicalClaimRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));
        validateEligibility(employee, request.contractOverride());

        EmployeeDependent dependent = null;
        if (request.dependentId() != null) {
            dependent = employeeDependentRepository.findById(request.dependentId())
                    .orElseThrow(() -> new MasterDataNotFoundException("Employee Dependent", request.dependentId()));
        }

        MedicalClaim claim = new MedicalClaim(request.claimNumber(), employee, dependent);
        BigDecimal totalClaimed = BigDecimal.ZERO;
        for (MedicalClaimItemRequest itemRequest : request.items()) {
            totalClaimed = totalClaimed.add(itemRequest.claimedAmount());
        }
        claim.setTotalClaimedAmount(totalClaimed);
        MedicalClaim saved = medicalClaimRepository.saveAndFlush(claim);

        for (MedicalClaimItemRequest itemRequest : request.items()) {
            MedicalClaimItem item = new MedicalClaimItem(saved, itemRequest.expenseType(), itemRequest.claimedAmount(),
                    itemRequest.billNumber(), itemRequest.billDate());
            item.setRemarks(itemRequest.remarks());
            medicalClaimItemRepository.save(item);
        }

        return toResponse(saved);
    }

    public MedicalClaimResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    public Page<MedicalClaimResponse> list(Pageable pageable) {
        return medicalClaimRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public MedicalClaimResponse submit(Long id) {
        MedicalClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.DRAFT);

        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        claim.setSubmissionDate(LocalDate.now());
        return toResponse(claim);
    }

    @Transactional
    public MedicalClaimResponse verifyByHr(Long id, MedicalClaimVerifyRequest request) {
        MedicalClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.SUBMITTED);

        List<MedicalClaimItem> items = medicalClaimItemRepository.findByMedicalClaimId(id);
        Map<Long, MedicalClaimItem> itemsById = items.stream()
                .collect(Collectors.toMap(MedicalClaimItem::getId, item -> item));

        if (request.items().size() != items.size()) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " has " + items.size() + " item(s); verification must cover all of them");
        }

        BigDecimal totalAllowed = BigDecimal.ZERO;
        for (MedicalClaimVerifyRequest.ItemAllowedAmount allowed : request.items()) {
            MedicalClaimItem item = itemsById.get(allowed.itemId());
            if (item == null) {
                throw new BusinessRuleViolationException(
                        "Item " + allowed.itemId() + " does not belong to " + ENTITY_NAME + " " + id);
            }
            if (allowed.allowedAmount().compareTo(item.getClaimedAmount()) > 0) {
                throw new BusinessRuleViolationException(
                        "Allowed amount for item " + item.getId() + " cannot exceed its claimed amount");
            }
            item.setAllowedAmount(allowed.allowedAmount());
            totalAllowed = totalAllowed.add(allowed.allowedAmount());
        }

        claim.setTotalAllowedAmount(totalAllowed);
        claim.setVerifiedBy(request.verifiedBy());
        claim.setStatus(ReimbursementClaimStatus.VERIFIED_BY_HR);
        return toResponse(claim);
    }

    @Transactional
    public MedicalClaimResponse approveByFinance(Long id, String approvedBy) {
        MedicalClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.VERIFIED_BY_HR);

        claim.setApprovedByFinance(approvedBy);
        claim.setStatus(ReimbursementClaimStatus.APPROVED_BY_FINANCE);
        return toResponse(claim);
    }

    @Transactional
    public MedicalClaimResponse reject(Long id) {
        MedicalClaim claim = findOrThrow(id);
        if (claim.getStatus() != ReimbursementClaimStatus.SUBMITTED
                && claim.getStatus() != ReimbursementClaimStatus.VERIFIED_BY_HR) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " cannot be rejected from status " + claim.getStatus());
        }
        claim.setStatus(ReimbursementClaimStatus.REJECTED);
        return toResponse(claim);
    }

    private void validateEligibility(Employee employee, boolean contractOverride) {
        if (!contractOverride && employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new BusinessRuleViolationException(
                    "Employee " + employee.getId() + " is not eligible for medical reimbursement "
                            + "(not ACTIVE) unless contractOverride is set");
        }
    }

    private void requireStatus(MedicalClaim claim, ReimbursementClaimStatus expected) {
        if (claim.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + claim.getId() + " must be " + expected + " but is " + claim.getStatus());
        }
    }

    private MedicalClaimResponse toResponse(MedicalClaim claim) {
        List<MedicalClaimItemResponse> items = medicalClaimItemRepository.findByMedicalClaimId(claim.getId()).stream()
                .map(MedicalClaimItemResponse::from)
                .toList();
        return MedicalClaimResponse.from(claim, items);
    }

    private MedicalClaim findOrThrow(Long id) {
        return medicalClaimRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
