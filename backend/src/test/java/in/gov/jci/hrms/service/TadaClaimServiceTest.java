package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TadaClaimRequest;
import in.gov.jci.hrms.dto.TadaClaimResponse;
import in.gov.jci.hrms.dto.TadaClaimVerifyRequest;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TadaClaimServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long TOUR_REQUEST_ID = 2L;
    private static final Long CLAIM_ID = 3L;
    private static final Long DESIGNATION_ID = 20L;

    @Mock
    private TadaClaimRepository tadaClaimRepository;
    @Mock
    private TourRequestRepository tourRequestRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private TadaRateMasterRepository tadaRateMasterRepository;

    private TadaClaimService tadaClaimService;
    private Employee employee;
    private Designation designation;
    private TourRequest tourRequest;

    @BeforeEach
    void setUp() {
        tadaClaimService = new TadaClaimService(tadaClaimRepository, tourRequestRepository, employeeRepository, tadaRateMasterRepository);

        Department department = new Department("ENG", "Engineering");
        designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", DESIGNATION_ID);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        tourRequest = new TourRequest("TR-2026-001", employee, "Client visit", "Delhi", "Mumbai",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3), false);
        ReflectionTestUtils.setField(tourRequest, "id", TOUR_REQUEST_ID);
    }

    private TadaClaim claimFrom(Long id, BigDecimal claimed) {
        TadaClaim claim = new TadaClaim(tourRequest, "TC-2026-001", employee, claimed);
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    private TadaRateMaster rate(BigDecimal roomRent, BigDecimal dailyAllowance) {
        return new TadaRateMaster(designation, CityClass.X, roomRent, dailyAllowance, true);
    }

    // ---- computeDaPercentage (FR-TADA.5) ----

    @Test
    void computeDaPercentage_belowThreeHours_isZeroPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("2.99"))).isEqualByComparingTo("0");
    }

    @Test
    void computeDaPercentage_atExactlyThreeHours_isFiftyPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("3"))).isEqualByComparingTo("50");
    }

    @Test
    void computeDaPercentage_atExactlySixHours_isFiftyPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("6"))).isEqualByComparingTo("50");
    }

    @Test
    void computeDaPercentage_justAboveSixHours_isSeventyPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("6.01"))).isEqualByComparingTo("70");
    }

    @Test
    void computeDaPercentage_atExactlyEightHours_isSeventyPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("8"))).isEqualByComparingTo("70");
    }

    @Test
    void computeDaPercentage_justAboveEightHours_isHundredPercent() {
        assertThat(tadaClaimService.computeDaPercentage(new BigDecimal("8.01"))).isEqualByComparingTo("100");
    }

    // ---- create ----

    @Test
    void create_savesInDraftStatus() {
        when(tourRequestRepository.findById(TOUR_REQUEST_ID)).thenReturn(Optional.of(tourRequest));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(tadaClaimRepository.saveAndFlush(any(TadaClaim.class))).thenAnswer(inv -> {
            TadaClaim saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", CLAIM_ID);
            return saved;
        });

        TadaClaimResponse response = tadaClaimService.create(
                new TadaClaimRequest(TOUR_REQUEST_ID, EMPLOYEE_ID, "TC-2026-001", new BigDecimal("5000.00")));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.DRAFT);
        assertThat(response.outOfPocketClaimed()).isEqualByComparingTo("5000.00");
    }

    @Test
    void create_whenTourRequestMissing_throwsMasterDataNotFoundException() {
        when(tourRequestRepository.findById(TOUR_REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tadaClaimService.create(
                new TadaClaimRequest(TOUR_REQUEST_ID, EMPLOYEE_ID, "TC-2026-001", new BigDecimal("5000.00"))))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(tourRequestRepository.findById(TOUR_REQUEST_ID)).thenReturn(Optional.of(tourRequest));
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tadaClaimService.create(
                new TadaClaimRequest(TOUR_REQUEST_ID, EMPLOYEE_ID, "TC-2026-001", new BigDecimal("5000.00"))))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    // ---- verifyByHr (ceiling check) ----

    @Test
    void verifyByHr_withinCeiling_succeeds() {
        // 3-day tour, hoursAway=7 -> 70% DA. roomRent=1000, dailyAllowance=500 -> maxPerDay = 1000 + 350 = 1350; maxTotal = 4050.
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("4000.00"));
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(tadaRateMasterRepository.findByDesignationIdAndCityClassAndActiveTrue(DESIGNATION_ID, CityClass.X))
                .thenReturn(Optional.of(rate(new BigDecimal("1000.00"), new BigDecimal("500.00"))));

        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("4000.00"),
                new BigDecimal("7"), CityClass.X);

        TadaClaimResponse response = tadaClaimService.verifyByHr(CLAIM_ID, request);

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.VERIFIED_BY_HR);
        assertThat(response.outOfPocketAllowed()).isEqualByComparingTo("4000.00");
    }

    @Test
    void verifyByHr_exceedingCeiling_throwsBusinessRuleViolationException() {
        // Same ceiling as above (4050.00), but HR tries to allow more than that.
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("5000.00"));
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(tadaRateMasterRepository.findByDesignationIdAndCityClassAndActiveTrue(DESIGNATION_ID, CityClass.X))
                .thenReturn(Optional.of(rate(new BigDecimal("1000.00"), new BigDecimal("500.00"))));

        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("5000.00"),
                new BigDecimal("7"), CityClass.X);

        assertThatThrownBy(() -> tadaClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("ceiling");
    }

    @Test
    void verifyByHr_whenAllowedExceedsClaimed_throwsBusinessRuleViolationException() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("2000.00"),
                new BigDecimal("7"), CityClass.X);

        assertThatThrownBy(() -> tadaClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("claimed amount");
    }

    @Test
    void verifyByHr_whenNoRateConfigured_throwsBusinessRuleViolationException() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(tadaRateMasterRepository.findByDesignationIdAndCityClassAndActiveTrue(DESIGNATION_ID, CityClass.X))
                .thenReturn(Optional.empty());

        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("500.00"),
                new BigDecimal("7"), CityClass.X);

        assertThatThrownBy(() -> tadaClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyByHr_whenNotSubmitted_throwsBusinessRuleViolationException() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        TadaClaimVerifyRequest request = new TadaClaimVerifyRequest("hr.verifier", new BigDecimal("500.00"),
                new BigDecimal("7"), CityClass.X);

        assertThatThrownBy(() -> tadaClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- approveByFinance / reject ----

    @Test
    void approveByFinance_movesVerifiedToApproved() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        claim.setStatus(ReimbursementClaimStatus.VERIFIED_BY_HR);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        TadaClaimResponse response = tadaClaimService.approveByFinance(CLAIM_ID, "finance.officer");

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.APPROVED_BY_FINANCE);
    }

    @Test
    void reject_fromSubmitted_succeeds() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        TadaClaimResponse response = tadaClaimService.reject(CLAIM_ID);

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.REJECTED);
    }

    @Test
    void reject_fromApproved_throwsBusinessRuleViolationException() {
        TadaClaim claim = claimFrom(CLAIM_ID, new BigDecimal("1000.00"));
        claim.setStatus(ReimbursementClaimStatus.APPROVED_BY_FINANCE);
        when(tadaClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> tadaClaimService.reject(CLAIM_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
