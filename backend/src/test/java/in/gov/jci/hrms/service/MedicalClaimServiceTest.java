package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.MedicalClaimItemRequest;
import in.gov.jci.hrms.dto.MedicalClaimRequest;
import in.gov.jci.hrms.dto.MedicalClaimResponse;
import in.gov.jci.hrms.dto.MedicalClaimVerifyRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExpenseType;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class MedicalClaimServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long CLAIM_ID = 10L;

    @Mock
    private MedicalClaimRepository medicalClaimRepository;
    @Mock
    private MedicalClaimItemRepository medicalClaimItemRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeDependentRepository employeeDependentRepository;

    private MedicalClaimService medicalClaimService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        medicalClaimService = new MedicalClaimService(medicalClaimRepository, medicalClaimItemRepository,
                employeeRepository, employeeDependentRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    }

    private MedicalClaimRequest validRequest() {
        return new MedicalClaimRequest("MC-2026-001", EMPLOYEE_ID, null, false, List.of(
                new MedicalClaimItemRequest(ExpenseType.CONSULTATION, new BigDecimal("500.00"), "BILL-1", LocalDate.of(2026, 1, 5), null),
                new MedicalClaimItemRequest(ExpenseType.MEDICINE, new BigDecimal("300.00"), "BILL-2", LocalDate.of(2026, 1, 6), null)
        ));
    }

    private MedicalClaim claimFrom(Long id, MedicalClaimRequest request) {
        MedicalClaim claim = new MedicalClaim(request.claimNumber(), employee, null);
        ReflectionTestUtils.setField(claim, "id", id);
        return claim;
    }

    // ---- create ----

    @Test
    void create_computesTotalClaimedAmountFromItems() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(medicalClaimRepository.saveAndFlush(any(MedicalClaim.class))).thenAnswer(inv -> {
            MedicalClaim claim = inv.getArgument(0);
            ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
            return claim;
        });
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of());

        MedicalClaimResponse response = medicalClaimService.create(validRequest());

        assertThat(response.totalClaimedAmount()).isEqualByComparingTo("800.00");
        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.DRAFT);
    }

    @Test
    void create_whenEmployeeInactiveAndNoOverride_throwsBusinessRuleViolationException() {
        employee.setStatus(EmployeeStatus.TERMINATED);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> medicalClaimService.create(validRequest()))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void create_whenEmployeeInactiveButOverrideSet_proceeds() {
        employee.setStatus(EmployeeStatus.TERMINATED);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(medicalClaimRepository.saveAndFlush(any(MedicalClaim.class))).thenAnswer(inv -> {
            MedicalClaim claim = inv.getArgument(0);
            ReflectionTestUtils.setField(claim, "id", CLAIM_ID);
            return claim;
        });
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of());

        MedicalClaimRequest override = new MedicalClaimRequest("MC-2026-002", EMPLOYEE_ID, null, true, List.of(
                new MedicalClaimItemRequest(ExpenseType.DENTAL, new BigDecimal("100.00"), "BILL-3", LocalDate.of(2026, 1, 1), null)
        ));

        MedicalClaimResponse response = medicalClaimService.create(override);

        assertThat(response.totalClaimedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void create_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalClaimService.create(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    // ---- submit ----

    @Test
    void submit_movesDraftToSubmittedAndSetsSubmissionDate() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of());

        MedicalClaimResponse response = medicalClaimService.submit(CLAIM_ID);

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.SUBMITTED);
        assertThat(response.submissionDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void submit_whenNotDraft_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> medicalClaimService.submit(CLAIM_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- verifyByHr ----

    @Test
    void verifyByHr_setsPerItemAllowedAmountsAndTotal() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        MedicalClaimItem item1 = new MedicalClaimItem(claim, ExpenseType.CONSULTATION, new BigDecimal("500.00"), "BILL-1", LocalDate.now());
        ReflectionTestUtils.setField(item1, "id", 100L);
        MedicalClaimItem item2 = new MedicalClaimItem(claim, ExpenseType.MEDICINE, new BigDecimal("300.00"), "BILL-2", LocalDate.now());
        ReflectionTestUtils.setField(item2, "id", 101L);

        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of(item1, item2));

        MedicalClaimVerifyRequest request = new MedicalClaimVerifyRequest("hr.verifier", List.of(
                new MedicalClaimVerifyRequest.ItemAllowedAmount(100L, new BigDecimal("450.00")),
                new MedicalClaimVerifyRequest.ItemAllowedAmount(101L, new BigDecimal("300.00"))
        ));

        MedicalClaimResponse response = medicalClaimService.verifyByHr(CLAIM_ID, request);

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.VERIFIED_BY_HR);
        assertThat(response.totalAllowedAmount()).isEqualByComparingTo("750.00");
        assertThat(item1.getAllowedAmount()).isEqualByComparingTo("450.00");
    }

    @Test
    void verifyByHr_whenItemAllowedAmountExceedsClaimed_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        MedicalClaimItem item1 = new MedicalClaimItem(claim, ExpenseType.CONSULTATION, new BigDecimal("500.00"), "BILL-1", LocalDate.now());
        ReflectionTestUtils.setField(item1, "id", 100L);

        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of(item1));

        MedicalClaimVerifyRequest request = new MedicalClaimVerifyRequest("hr.verifier", List.of(
                new MedicalClaimVerifyRequest.ItemAllowedAmount(100L, new BigDecimal("999.00"))
        ));

        assertThatThrownBy(() -> medicalClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyByHr_whenItemCountMismatch_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        MedicalClaimItem item1 = new MedicalClaimItem(claim, ExpenseType.CONSULTATION, new BigDecimal("500.00"), "BILL-1", LocalDate.now());
        ReflectionTestUtils.setField(item1, "id", 100L);
        MedicalClaimItem item2 = new MedicalClaimItem(claim, ExpenseType.MEDICINE, new BigDecimal("300.00"), "BILL-2", LocalDate.now());
        ReflectionTestUtils.setField(item2, "id", 101L);

        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of(item1, item2));

        MedicalClaimVerifyRequest request = new MedicalClaimVerifyRequest("hr.verifier", List.of(
                new MedicalClaimVerifyRequest.ItemAllowedAmount(100L, new BigDecimal("450.00"))
        ));

        assertThatThrownBy(() -> medicalClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyByHr_whenNotSubmitted_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        MedicalClaimVerifyRequest request = new MedicalClaimVerifyRequest("hr.verifier", List.of(
                new MedicalClaimVerifyRequest.ItemAllowedAmount(100L, new BigDecimal("450.00"))
        ));

        assertThatThrownBy(() -> medicalClaimService.verifyByHr(CLAIM_ID, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- approveByFinance ----

    @Test
    void approveByFinance_movesVerifiedToApproved() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.VERIFIED_BY_HR);
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of());

        MedicalClaimResponse response = medicalClaimService.approveByFinance(CLAIM_ID, "finance.officer");

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.APPROVED_BY_FINANCE);
        assertThat(response.approvedByFinance()).isEqualTo("finance.officer");
    }

    @Test
    void approveByFinance_whenNotVerified_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> medicalClaimService.approveByFinance(CLAIM_ID, "finance.officer"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- reject ----

    @Test
    void reject_fromSubmitted_succeeds() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        claim.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(medicalClaimItemRepository.findByMedicalClaimId(CLAIM_ID)).thenReturn(List.of());

        MedicalClaimResponse response = medicalClaimService.reject(CLAIM_ID);

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.REJECTED);
    }

    @Test
    void reject_fromDraft_throwsBusinessRuleViolationException() {
        MedicalClaim claim = claimFrom(CLAIM_ID, validRequest());
        when(medicalClaimRepository.findById(CLAIM_ID)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> medicalClaimService.reject(CLAIM_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(medicalClaimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> medicalClaimService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
