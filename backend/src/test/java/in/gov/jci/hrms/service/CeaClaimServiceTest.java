package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CeaClaimBillPassRequest;
import in.gov.jci.hrms.dto.CeaClaimResponse;
import in.gov.jci.hrms.dto.CeaClaimSubmitRequest;
import in.gov.jci.hrms.dto.CeaClaimVerifyRequest;
import in.gov.jci.hrms.entity.CeaClaimStatus;
import in.gov.jci.hrms.entity.CeaClaimType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeCeaClaim;
import in.gov.jci.hrms.entity.EmployeeDependent;
import in.gov.jci.hrms.entity.FamilyRelationshipType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CeaClaimServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final String ACADEMIC_YEAR = "2026-2027";

    @Mock private EmployeeCeaClaimRepository claimRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeDependentRepository dependentRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;

    private CeaClaimService service;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new CeaClaimService(claimRepository, employeeRepository, dependentRepository, payrollStatutoryParameterRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(1990, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(claimRepository.saveAndFlush(any(EmployeeCeaClaim.class))).thenAnswer(inv -> inv.getArgument(0));
        when(claimRepository.findByEmployeeIdAndAcademicYearAndClaimStatusNot(EMPLOYEE_ID, ACADEMIC_YEAR, CeaClaimStatus.REJECTED))
                .thenReturn(List.of());
    }

    private EmployeeDependent son(long id, LocalDate dateOfBirth, boolean divyang, boolean isDependent, boolean multipleBirth) {
        EmployeeDependent dependent = new EmployeeDependent(employee, "Junior Rao", FamilyRelationshipType.SON, isDependent, false);
        dependent.setDateOfBirth(dateOfBirth);
        dependent.setDivyang(divyang);
        dependent.setMultipleBirthSecondDelivery(multipleBirth);
        ReflectionTestUtils.setField(dependent, "id", id);
        when(dependentRepository.findById(id)).thenReturn(Optional.of(dependent));
        return dependent;
    }

    private CeaClaimSubmitRequest request(long dependentId, LocalDate periodFrom, LocalDate periodTo, BigDecimal claimedAmount) {
        return new CeaClaimSubmitRequest("CEA/" + dependentId, dependentId, ACADEMIC_YEAR, CeaClaimType.CEA,
                "ABC School", "REG-1", "V", periodFrom, periodTo, claimedAmount, null);
    }

    // ---- Age eligibility: <=20 standard, <=22 Divyang ----

    @Test
    void submitClaim_ageWithinStandardLimit_succeeds() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(10L, periodTo.minusYears(15), false, true, false);

        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(10L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00")));

        assertThat(response.claimStatus()).isEqualTo(CeaClaimStatus.SUBMITTED);
    }

    @Test
    void submitClaim_ageExceedsStandardLimit_throws() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(11L, periodTo.minusYears(21), false, true, false);

        assertThatThrownBy(() -> service.submitClaim(EMPLOYEE_ID, request(11L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00"))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("20");
    }

    @Test
    void submitClaim_divyangAgeWithinExtendedLimit_succeeds() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        // Age 22 - would fail the standard <=20 cutoff, but the Divyang <=22 extension covers it.
        son(12L, periodTo.minusYears(22), true, true, false);

        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(12L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00")));

        assertThat(response.claimStatus()).isEqualTo(CeaClaimStatus.SUBMITTED);
    }

    @Test
    void submitClaim_divyangAgeExceedsExtendedLimit_throws() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(13L, periodTo.minusYears(23), true, true, false);

        assertThatThrownBy(() -> service.submitClaim(EMPLOYEE_ID, request(13L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00"))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("22");
    }

    // ---- 2-child-per-academic-year rule, with the multiple-birth exception ----

    private EmployeeCeaClaim existingClaimFor(EmployeeDependent dependent) {
        EmployeeCeaClaim claim = new EmployeeCeaClaim("CEA/existing/" + dependent.getId(), employee, dependent, ACADEMIC_YEAR,
                CeaClaimType.CEA, "Some School", "III", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31),
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        return claim;
    }

    @Test
    void submitClaim_thirdDistinctChildWithoutMultipleBirthFlag_isRejected() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        EmployeeDependent firstChild = son(20L, periodTo.minusYears(10), false, true, false);
        EmployeeDependent secondChild = son(21L, periodTo.minusYears(8), false, true, false);
        when(claimRepository.findByEmployeeIdAndAcademicYearAndClaimStatusNot(EMPLOYEE_ID, ACADEMIC_YEAR, CeaClaimStatus.REJECTED))
                .thenReturn(List.of(existingClaimFor(firstChild), existingClaimFor(secondChild)));

        EmployeeDependent thirdChild = son(22L, periodTo.minusYears(6), false, true, false);

        assertThatThrownBy(() -> service.submitClaim(EMPLOYEE_ID, request(22L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00"))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("2 children");
    }

    @Test
    void submitClaim_thirdDistinctChildFromMultipleBirth_isAllowed() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        EmployeeDependent firstChild = son(30L, periodTo.minusYears(10), false, true, false);
        EmployeeDependent secondChild = son(31L, periodTo.minusYears(8), false, true, false);
        when(claimRepository.findByEmployeeIdAndAcademicYearAndClaimStatusNot(EMPLOYEE_ID, ACADEMIC_YEAR, CeaClaimStatus.REJECTED))
                .thenReturn(List.of(existingClaimFor(firstChild), existingClaimFor(secondChild)));

        // The 3rd distinct child this year, but flagged as a multiple-birth (twin) delivery.
        EmployeeDependent thirdChild = son(32L, periodTo.minusYears(6), false, true, true);

        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(32L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00")));

        assertThat(response.claimStatus()).isEqualTo(CeaClaimStatus.SUBMITTED);
    }

    @Test
    void submitClaim_secondClaimForAlreadyCountedChild_doesNotConsumeAnotherSlot() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        EmployeeDependent firstChild = son(40L, periodTo.minusYears(10), false, true, false);
        EmployeeDependent secondChild = son(41L, periodTo.minusYears(8), false, true, false);
        // Both existing claims belong to the SAME child (firstChild) - only 1 distinct child so far.
        when(claimRepository.findByEmployeeIdAndAcademicYearAndClaimStatusNot(EMPLOYEE_ID, ACADEMIC_YEAR, CeaClaimStatus.REJECTED))
                .thenReturn(List.of(existingClaimFor(firstChild)));

        // secondChild is the true 2nd distinct child - must succeed.
        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(41L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("30000.00")));

        assertThat(response.claimStatus()).isEqualTo(CeaClaimStatus.SUBMITTED);
    }

    // ---- Admissible amount = MIN(claimed, monthlyRate * totalMonths) ----

    @Test
    void submitClaim_admissibleAmountCappedAtStandardRateTimesMonths() {
        LocalDate periodFrom = LocalDate.of(2026, 4, 1);
        LocalDate periodTo = LocalDate.of(2027, 3, 31); // 12 months inclusive
        son(50L, periodTo.minusYears(10), false, true, false);

        // Claimed far above the cap: 12 * 2812.50 = 33,750.00
        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(50L, periodFrom, periodTo, new BigDecimal("100000.00")));

        assertThat(response.admissibleAmount()).isEqualByComparingTo("33750.00");
    }

    @Test
    void submitClaim_admissibleAmountUsesClaimedWhenBelowCap() {
        LocalDate periodFrom = LocalDate.of(2026, 4, 1);
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(51L, periodTo.minusYears(10), false, true, false);

        CeaClaimResponse response = service.submitClaim(EMPLOYEE_ID, request(51L, periodFrom, periodTo, new BigDecimal("5000.00")));

        assertThat(response.admissibleAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void submitClaim_duplicateClaimNo_throwsMasterDataConflictException() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(60L, periodTo.minusYears(10), false, true, false);
        when(claimRepository.existsByClaimNo("CEA/60")).thenReturn(true);

        assertThatThrownBy(() -> service.submitClaim(EMPLOYEE_ID, request(60L, LocalDate.of(2026, 4, 1), periodTo, new BigDecimal("5000.00"))))
                .isInstanceOf(MasterDataConflictException.class);
    }

    // ---- Full lifecycle: SUBMITTED -> VERIFIED -> BILL_PASSED ----

    @Test
    void fullLifecycle_submittedThenVerifiedThenBillPassed() {
        LocalDate periodTo = LocalDate.of(2027, 3, 31);
        son(70L, periodTo.minusYears(10), false, true, false);
        Employee verifier = employeeWithId(2L);
        Employee accountsOfficer = employeeWithId(3L);

        EmployeeCeaClaim claim = new EmployeeCeaClaim("CEA/70", employee, dependentRepository.findById(70L).orElseThrow(),
                ACADEMIC_YEAR, CeaClaimType.CEA, "ABC School", "V", LocalDate.of(2026, 4, 1), periodTo,
                new BigDecimal("30000.00"), new BigDecimal("28125.00"));
        ReflectionTestUtils.setField(claim, "id", 900L);
        when(claimRepository.findById(900L)).thenReturn(Optional.of(claim));

        CeaClaimResponse verified = service.verifyClaim(900L, 2L, new CeaClaimVerifyRequest(new BigDecimal("27000.00")));
        assertThat(verified.claimStatus()).isEqualTo(CeaClaimStatus.VERIFIED);
        assertThat(verified.admissibleAmount()).isEqualByComparingTo("27000.00");
        assertThat(verified.verifiedByOfficerId()).isEqualTo(2L);

        CeaClaimResponse billPassed = service.passBill(900L, 3L, new CeaClaimBillPassRequest(
                new BigDecimal("27000.00"), "BILL/2027/001", LocalDate.of(2027, 4, 5), "SANCTION/2027/001", LocalDate.of(2027, 4, 5)));
        assertThat(billPassed.claimStatus()).isEqualTo(CeaClaimStatus.BILL_PASSED);
        assertThat(billPassed.passedAmount()).isEqualByComparingTo("27000.00");
        assertThat(billPassed.billNo()).isEqualTo("BILL/2027/001");
        assertThat(billPassed.passedByOfficerId()).isEqualTo(3L);
    }

    @Test
    void verifyClaim_notSubmitted_throws() {
        EmployeeCeaClaim claim = existingClaimFor(son(80L, LocalDate.of(2016, 1, 1), false, true, false));
        claim.setClaimStatus(CeaClaimStatus.VERIFIED);
        ReflectionTestUtils.setField(claim, "id", 901L);
        when(claimRepository.findById(901L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.verifyClaim(901L, 2L, new CeaClaimVerifyRequest(null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void passBill_notVerified_throws() {
        EmployeeCeaClaim claim = existingClaimFor(son(81L, LocalDate.of(2016, 1, 1), false, true, false));
        ReflectionTestUtils.setField(claim, "id", 902L);
        when(claimRepository.findById(902L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> service.passBill(902L, 3L, new CeaClaimBillPassRequest(
                BigDecimal.TEN, "B", LocalDate.now(), "S", LocalDate.now())))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void listAll_returnsEveryClaimRegardlessOfStatus() {
        EmployeeCeaClaim disbursed = existingClaimFor(son(82L, LocalDate.of(2016, 1, 1), false, true, false));
        disbursed.setClaimStatus(CeaClaimStatus.DISBURSED);
        EmployeeCeaClaim rejected = existingClaimFor(son(83L, LocalDate.of(2016, 1, 1), false, true, false));
        rejected.setClaimStatus(CeaClaimStatus.REJECTED);
        when(claimRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(disbursed, rejected));

        List<CeaClaimResponse> all = service.listAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(CeaClaimResponse::claimStatus)
                .containsExactly(CeaClaimStatus.DISBURSED, CeaClaimStatus.REJECTED);
    }

    private Employee employeeWithId(long id) {
        Employee e = new Employee("EMP-00" + id, "Officer", "Rao", "officer" + id + "@example.com", LocalDate.of(1980, 1, 1),
                new Department("ENG", "Engineering"), new Designation("Manager"));
        ReflectionTestUtils.setField(e, "id", id);
        when(employeeRepository.findById(id)).thenReturn(Optional.of(e));
        return e;
    }
}
