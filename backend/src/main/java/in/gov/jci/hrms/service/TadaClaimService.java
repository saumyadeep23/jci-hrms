package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TadaClaimRequest;
import in.gov.jci.hrms.dto.TadaClaimResponse;
import in.gov.jci.hrms.dto.TadaClaimVerifyRequest;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.ReimbursementClaimStatus;
import in.gov.jci.hrms.entity.TadaClaim;
import in.gov.jci.hrms.entity.TadaRateMaster;
import in.gov.jci.hrms.entity.TourRequest;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.TadaClaimRepository;
import in.gov.jci.hrms.repository.TadaRateMasterRepository;
import in.gov.jci.hrms.repository.TourRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 4-eyes lifecycle mirroring MedicalClaimService: Draft -> Submit -> HR
 * Verify (ceiling check against tada_rate_master) -> Finance Approve, with
 * Reject at the Submitted or Verified-by-HR checkpoints.
 *
 * FR-TADA.5 needs hours-away-from-headquarters, and the ceiling check needs
 * the destination's city class - neither is derivable from this schema:
 * tour_requests only stores dates (no time-of-day), and destination is
 * free text with no link to a city_class-bearing master (only ro_master
 * carries city_class, and there's no lookup from an arbitrary place name to
 * an RO). Both are therefore explicit inputs to verifyByHr() rather than
 * something computed from stored data - in practice an HR verifier would
 * read hours-away off the traveller's actual tickets/boarding passes.
 */
@Service
@Transactional(readOnly = true)
public class TadaClaimService {

    private static final String ENTITY_NAME = "TADA Claim";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal THREE_HOURS = new BigDecimal("3");
    private static final BigDecimal SIX_HOURS = new BigDecimal("6");
    private static final BigDecimal EIGHT_HOURS = new BigDecimal("8");
    private static final BigDecimal FIFTY_PERCENT = new BigDecimal("50");
    private static final BigDecimal SEVENTY_PERCENT = new BigDecimal("70");
    private static final BigDecimal HUNDRED_PERCENT = new BigDecimal("100");

    private final TadaClaimRepository tadaClaimRepository;
    private final TourRequestRepository tourRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final TadaRateMasterRepository tadaRateMasterRepository;

    public TadaClaimService(TadaClaimRepository tadaClaimRepository, TourRequestRepository tourRequestRepository,
                             EmployeeRepository employeeRepository, TadaRateMasterRepository tadaRateMasterRepository) {
        this.tadaClaimRepository = tadaClaimRepository;
        this.tourRequestRepository = tourRequestRepository;
        this.employeeRepository = employeeRepository;
        this.tadaRateMasterRepository = tadaRateMasterRepository;
    }

    @Transactional
    public TadaClaimResponse create(TadaClaimRequest request) {
        TourRequest tourRequest = tourRequestRepository.findById(request.tourRequestId())
                .orElseThrow(() -> new MasterDataNotFoundException("Tour Request", request.tourRequestId()));
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new EmployeeNotFoundException(request.employeeId()));

        TadaClaim claim = new TadaClaim(tourRequest, request.claimNumber(), employee, request.outOfPocketClaimed());
        return TadaClaimResponse.from(tadaClaimRepository.saveAndFlush(claim));
    }

    public TadaClaimResponse getById(Long id) {
        return TadaClaimResponse.from(findOrThrow(id));
    }

    public Page<TadaClaimResponse> list(Pageable pageable) {
        return tadaClaimRepository.findAll(pageable).map(TadaClaimResponse::from);
    }

    @Transactional
    public TadaClaimResponse submit(Long id) {
        TadaClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.DRAFT);

        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        claim.setSubmissionDate(LocalDate.now());
        return TadaClaimResponse.from(claim);
    }

    @Transactional
    public TadaClaimResponse verifyByHr(Long id, TadaClaimVerifyRequest request) {
        TadaClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.SUBMITTED);

        if (request.allowedAmount().compareTo(claim.getOutOfPocketClaimed()) > 0) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " allowed amount cannot exceed the claimed amount");
        }

        TadaRateMaster rate = tadaRateMasterRepository
                .findByDesignationIdAndCityClassAndActiveTrue(claim.getEmployee().getDesignation().getId(), request.cityClass())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "No active TADA rate configured for designation " + claim.getEmployee().getDesignation().getId()
                                + ", city class " + request.cityClass()));

        BigDecimal ceiling = computeCeiling(rate, request.hoursAway(), claim.getTourRequest());
        if (request.allowedAmount().compareTo(ceiling) > 0) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " allowed amount " + request.allowedAmount()
                            + " exceeds the TADA ceiling of " + ceiling);
        }

        claim.setOutOfPocketAllowed(request.allowedAmount());
        claim.setVerifiedBy(request.verifiedBy());
        claim.setStatus(ReimbursementClaimStatus.VERIFIED_BY_HR);
        return TadaClaimResponse.from(claim);
    }

    @Transactional
    public TadaClaimResponse approveByFinance(Long id, String approvedBy) {
        TadaClaim claim = findOrThrow(id);
        requireStatus(claim, ReimbursementClaimStatus.VERIFIED_BY_HR);

        claim.setApprovedByFinance(approvedBy);
        claim.setStatus(ReimbursementClaimStatus.APPROVED_BY_FINANCE);
        return TadaClaimResponse.from(claim);
    }

    @Transactional
    public TadaClaimResponse reject(Long id) {
        TadaClaim claim = findOrThrow(id);
        if (claim.getStatus() != ReimbursementClaimStatus.SUBMITTED
                && claim.getStatus() != ReimbursementClaimStatus.VERIFIED_BY_HR) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + id + " cannot be rejected from status " + claim.getStatus());
        }
        claim.setStatus(ReimbursementClaimStatus.REJECTED);
        return TadaClaimResponse.from(claim);
    }

    /**
     * FR-TADA.5: less than 3 hours away = 0% DA, 3-6h = 50%, 6-8h = 70%,
     * more than 8h = 100%. The "X-Yh" ranges in the SRS don't state which
     * side of each boundary they belong to except for the top bracket
     * (">8h"); this implementation resolves that ambiguity by treating each
     * bracket's upper bound as inclusive - i.e. exactly 3h and 6h fall in
     * the lower of their two adjacent brackets, consistent with ">8h"
     * (not ">=8h") being the stated top-bracket boundary.
     */
    public BigDecimal computeDaPercentage(BigDecimal hoursAway) {
        if (hoursAway.compareTo(THREE_HOURS) < 0) {
            return BigDecimal.ZERO;
        }
        if (hoursAway.compareTo(SIX_HOURS) <= 0) {
            return FIFTY_PERCENT;
        }
        if (hoursAway.compareTo(EIGHT_HOURS) <= 0) {
            return SEVENTY_PERCENT;
        }
        return HUNDRED_PERCENT;
    }

    /**
     * ceiling = (room_rent_ceiling + daily_allowance_ceiling * DA%) * tour days.
     */
    private BigDecimal computeCeiling(TadaRateMaster rate, BigDecimal hoursAway, TourRequest tourRequest) {
        BigDecimal daPercentage = computeDaPercentage(hoursAway);
        BigDecimal scaledDailyAllowance = rate.getDailyAllowanceCeiling()
                .multiply(daPercentage)
                .divide(HUNDRED, 10, RoundingMode.HALF_UP);
        BigDecimal maxPerDay = rate.getRoomRentCeiling().add(scaledDailyAllowance);

        long tourDays = ChronoUnit.DAYS.between(tourRequest.getStartDate(), tourRequest.getEndDate()) + 1;
        return maxPerDay.multiply(BigDecimal.valueOf(tourDays)).setScale(2, RoundingMode.HALF_UP);
    }

    private void requireStatus(TadaClaim claim, ReimbursementClaimStatus expected) {
        if (claim.getStatus() != expected) {
            throw new BusinessRuleViolationException(
                    ENTITY_NAME + " " + claim.getId() + " must be " + expected + " but is " + claim.getStatus());
        }
    }

    private TadaClaim findOrThrow(Long id) {
        return tadaClaimRepository.findById(id)
                .orElseThrow(() -> new MasterDataNotFoundException(ENTITY_NAME, id));
    }
}
