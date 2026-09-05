package in.gov.jci.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.EmployeeBankAccountRequest;
import in.gov.jci.hrms.dto.EmployeeRequest;
import in.gov.jci.hrms.dto.EmployeeResponse;
import in.gov.jci.hrms.dto.OnboardingDependentEntry;
import in.gov.jci.hrms.dto.OnboardingDraftResponse;
import in.gov.jci.hrms.dto.OnboardingDraftUpsertRequest;
import in.gov.jci.hrms.dto.OnboardingDocumentEntry;
import in.gov.jci.hrms.dto.OnboardingEmploymentStepRequest;
import in.gov.jci.hrms.dto.OnboardingFamilyStepRequest;
import in.gov.jci.hrms.dto.OnboardingNomineeEntry;
import in.gov.jci.hrms.dto.OnboardingPersonalDetailsRequest;
import in.gov.jci.hrms.dto.OnboardingSubmitResponse;
import in.gov.jci.hrms.dto.PastServiceRecordRequest;
import in.gov.jci.hrms.dto.QualificationRequest;
import in.gov.jci.hrms.entity.AddressType;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CourseType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmployeeOnboardingDraft;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.OnboardingStatus;
import in.gov.jci.hrms.entity.PastServiceOrganizationType;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.QualificationLevel;
import in.gov.jci.hrms.entity.RecruitmentMode;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.entity.VacancyStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.OnboardingDraftNotFoundException;
import in.gov.jci.hrms.repository.ContractualEngagementRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeAddressRepository;
import in.gov.jci.hrms.repository.EmployeeBankAccountRepository;
import in.gov.jci.hrms.repository.EmployeeDependentRepository;
import in.gov.jci.hrms.repository.EmployeeDocumentRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeFamilyDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeNomineeRepository;
import in.gov.jci.hrms.repository.EmployeeOnboardingDraftRepository;
import in.gov.jci.hrms.repository.EmployeePastServiceRecordRepository;
import in.gov.jci.hrms.repository.EmployeeQualificationRepository;
import in.gov.jci.hrms.repository.EmployeeRecruitmentDetailsRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeSocialProfileRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.OutsourcedDeploymentRepository;
import in.gov.jci.hrms.repository.OutsourcedSalaryBreakdownItemRepository;
import in.gov.jci.hrms.repository.PayScaleRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.VendorMasterRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeOnboardingServiceTest {

    private static final Long DRAFT_ID = 1L;
    private static final Long DEPARTMENT_ID = 10L;
    private static final Long DESIGNATION_ID = 20L;
    private static final Long POST_ID = 5L;
    private static final Long PAY_SCALE_ID = 50L;

    @Mock private EmployeeOnboardingDraftRepository draftRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeQualificationRepository qualificationRepository;
    @Mock private EmployeePastServiceRecordRepository pastServiceRecordRepository;
    @Mock private EmployeeNomineeRepository nomineeRepository;
    @Mock private EmployeeDependentRepository dependentRepository;
    @Mock private EmployeeAddressRepository addressRepository;
    @Mock private EmployeeBankAccountRepository bankAccountRepository;
    @Mock private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock private OutsourcedSalaryBreakdownItemRepository salaryBreakdownRepository;
    @Mock private EmployeeFamilyDetailsRepository familyDetailsRepository;
    @Mock private EmployeeRecruitmentDetailsRepository recruitmentDetailsRepository;
    @Mock private EmployeeDocumentRepository documentRepository;
    @Mock private EmployeeSocialProfileRepository socialProfileRepository;
    @Mock private PostMasterRepository postMasterRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private VendorMasterRepository vendorMasterRepository;
    @Mock private PayScaleRepository payScaleRepository;
    @Mock private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private ContractualEngagementRepository contractualEngagementRepository;
    @Mock private OutsourcedDeploymentRepository outsourcedDeploymentRepository;
    @Mock private EmployeeCodeGeneratorService employeeCodeGeneratorService;
    @Mock private EmployeeService employeeService;
    @Mock private PostIncumbencyService postIncumbencyService;

    private EmployeeOnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        onboardingService = new EmployeeOnboardingService(
                draftRepository, employeeRepository, qualificationRepository, pastServiceRecordRepository,
                nomineeRepository, dependentRepository, addressRepository, bankAccountRepository,
                employmentCategoryRepository, salaryBreakdownRepository, familyDetailsRepository,
                recruitmentDetailsRepository, documentRepository, socialProfileRepository, postMasterRepository,
                departmentRepository, designationRepository, vendorMasterRepository, payScaleRepository,
                gradeScaleMasterRepository, regularPayFixationRepository, contractualEngagementRepository,
                outsourcedDeploymentRepository, employeeCodeGeneratorService, employeeService, postIncumbencyService,
                validator, objectMapper);
    }

    private EmployeeOnboardingDraft newDraft() {
        EmployeeOnboardingDraft draft = new EmployeeOnboardingDraft("OB-2026-000001", "0001", "hr-admin");
        ReflectionTestUtils.setField(draft, "id", DRAFT_ID);
        return draft;
    }

    private void stubFindDraft(EmployeeOnboardingDraft draft) {
        when(draftRepository.findById(DRAFT_ID)).thenReturn(Optional.of(draft));
    }

    private OnboardingPersonalDetailsRequest personal() {
        return new OnboardingPersonalDetailsRequest(
                Salutation.MS, "Asha", null, "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null,
                "Indian", null, "ABCDE1234F", "CPF00001", "123456789012", "asha.rao@example.com", null, "9876543210", null);
    }

    private EmployeeAddressRequest address() {
        return new EmployeeAddressRequest(AddressType.PRESENT, "221B Baker Street", null, null, null,
                "Bangalore", "Bangalore", "Karnataka", "560001");
    }

    private EmployeeBankAccountRequest banking() {
        return new EmployeeBankAccountRequest("State Bank of India", "MG Road", "1234567890", "1234567890",
                "SBIN0001234", null);
    }

    private OnboardingEmploymentStepRequest employmentRegular() {
        return new OnboardingEmploymentStepRequest(
                EmploymentCategory.REGULAR, null, null, POST_ID, PAY_SCALE_ID, new BigDecimal("45000.00"),
                null, null, null, null, null, null, null, null, null, null, null, "E1", null,
                null, null, null, null,
                LocalDate.of(2026, 1, 15), 2026, RecruitmentMode.DIRECT_RECRUITMENT, "Written Test",
                null, null, "APT-001", LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 10));
    }

    private GradeScaleMaster gradeScale() {
        return new GradeScaleMaster("E1", Cadre.EXECUTIVE, 9, false, new BigDecimal("30000.00"), new BigDecimal("100000.00"));
    }

    private OnboardingFamilyStepRequest family() {
        return new OnboardingFamilyStepRequest("Ram Rao", "Sita Rao", null, null,
                List.of(new OnboardingDependentEntry("Sita Rao", "Mother", LocalDate.of(1965, 1, 1), true, true)),
                List.of(new OnboardingNomineeEntry("Sita Rao", "Mother", new BigDecimal("100.00"), "PF")));
    }

    private List<OnboardingDocumentEntry> documents() {
        return List.of(new OnboardingDocumentEntry(in.gov.jci.hrms.entity.DocumentCategory.PHOTO, "Photo", "s3://photo.jpg", null));
    }

    private OnboardingDraftUpsertRequest fullUpsertRequest(Long draftId) {
        return new OnboardingDraftUpsertRequest(draftId, null, personal(), address(), null, true, banking(),
                List.of(new QualificationRequest(QualificationLevel.GRADUATION, "B.Tech", null, "Delhi University",
                        "IIT Delhi", 2015, new BigDecimal("82.50"), null, CourseType.FULL_TIME, true, null)),
                List.of(new PastServiceRecordRequest("TCS", PastServiceOrganizationType.PRIVATE_SECTOR, "Engineer",
                        LocalDate.of(2018, 1, 1), LocalDate.of(2023, 1, 1), null, null, null, false, null, null, null, null)),
                employmentRegular(), family(), documents(), null);
    }

    @Test
    void upsert_withNoDraftId_startsNewDraft() {
        when(employeeCodeGeneratorService.generateNext()).thenReturn("0001");
        when(draftRepository.nextDraftCodeSequence()).thenReturn(1L);
        when(draftRepository.saveAndFlush(any(EmployeeOnboardingDraft.class))).thenAnswer(invocation -> {
            EmployeeOnboardingDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", DRAFT_ID);
            return draft;
        });

        OnboardingDraftResponse response = onboardingService.upsert(
                new OnboardingDraftUpsertRequest(null, 1, personal(), null, null, null, null, null, null, null, null, null, null),
                "hr-admin");

        assertThat(response.id()).isEqualTo(DRAFT_ID);
        assertThat(response.employeeCode()).isEqualTo("0001");
        assertThat(response.draftCode()).isEqualTo("OB-" + Year.now().getValue() + "-000001");
        assertThat(response.status()).isEqualTo(OnboardingStatus.IN_PROGRESS);
        assertThat(response.personal()).isEqualTo(personal());
    }

    @Test
    void upsert_withDraftId_mergesWithoutClobberingUntouchedGroups() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        onboardingService.upsert(new OnboardingDraftUpsertRequest(DRAFT_ID, 1, personal(), null, null, null, null, null, null, null, null, null, null), null);

        OnboardingDraftResponse response = onboardingService.upsert(
                new OnboardingDraftUpsertRequest(DRAFT_ID, 2, null, address(), null, true, null, null, null, null, null, null, null), null);

        assertThat(response.personal()).isEqualTo(personal());
        assertThat(response.presentAddress()).isEqualTo(address());
        assertThat(response.currentStep()).isEqualTo(2);
    }

    @Test
    void upsert_onDraftNotInProgress_throwsBusinessRuleViolationException() {
        EmployeeOnboardingDraft draft = newDraft();
        draft.setStatus(OnboardingStatus.CANCELLED);
        stubFindDraft(draft);

        assertThatThrownBy(() -> onboardingService.upsert(
                new OnboardingDraftUpsertRequest(DRAFT_ID, null, personal(), null, null, null, null, null, null, null, null, null, null), null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getById_whenMissing_throwsOnboardingDraftNotFoundException() {
        when(draftRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> onboardingService.getById(99L))
                .isInstanceOf(OnboardingDraftNotFoundException.class);
    }

    @Test
    void deleteDraft_deletesDraftPermanently() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);

        onboardingService.deleteDraft(DRAFT_ID);

        verify(draftRepository).delete(draft);
    }

    @Test
    void finalizeOnboarding_withMissingGroups_throwsBusinessRuleViolationException() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        onboardingService.upsert(new OnboardingDraftUpsertRequest(DRAFT_ID, null, personal(), null, null, null, null, null, null, null, null, null, null), null);

        assertThatThrownBy(() -> onboardingService.finalizeOnboarding(DRAFT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("presentAddress");
        verify(employeeService, never()).create(any(), any());
    }

    @Test
    void finalizeOnboarding_whenNomineeSharesDoNotSumTo100_throwsBusinessRuleViolationException() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        OnboardingFamilyStepRequest badFamily = new OnboardingFamilyStepRequest("Ram Rao", null, null, null, List.of(),
                List.of(new OnboardingNomineeEntry("Sita Rao", "Mother", new BigDecimal("60.00"), "PF")));
        onboardingService.upsert(new OnboardingDraftUpsertRequest(
                DRAFT_ID, null, personal(), address(), null, true, banking(), List.of(), List.of(), employmentRegular(), badFamily, documents(), null), null);

        assertThatThrownBy(() -> onboardingService.finalizeOnboarding(DRAFT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("100%");
    }

    @Test
    void finalizeOnboarding_happyPath_createsEmployeeAndPostIncumbencyAndMarksSubmitted() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        onboardingService.upsert(fullUpsertRequest(DRAFT_ID), null);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", DEPARTMENT_ID);
        Designation designation = new Designation("Backend Developer");
        ReflectionTestUtils.setField(designation, "id", DESIGNATION_ID);
        PostMaster post = new PostMaster("POST-001", "Backend Developer", department, designation, true);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        post.setVacancyStatus(VacancyStatus.VACANT);
        post.setBudgeted(true);
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        in.gov.jci.hrms.entity.PayScale payScale = new in.gov.jci.hrms.entity.PayScale(
                in.gov.jci.hrms.entity.ScaleType.IDA, "E1", new BigDecimal("40000.00"), new BigDecimal("60000.00"),
                new BigDecimal("3.00"), true);
        ReflectionTestUtils.setField(payScale, "id", PAY_SCALE_ID);
        when(payScaleRepository.findById(PAY_SCALE_ID)).thenReturn(Optional.of(payScale));
        when(gradeScaleMasterRepository.findByScaleCode("E1")).thenReturn(Optional.of(gradeScale()));

        EmployeeResponse createdEmployee = new EmployeeResponse(
                100L, "0001", "EMP000001", Salutation.MS, "Asha", null, "Rao", "Asha Rao", Gender.FEMALE,
                LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null, "Indian", null, "ABCDE1234F", "XXXX-XXXX-9012",
                "asha.rao@example.com", null, "9876543210", null, LocalDate.of(2026, 1, 15),
                DEPARTMENT_ID, "Engineering", DESIGNATION_ID, "Backend Developer", null, null, null, null,
                PAY_SCALE_ID, "E1", EmployeeStatus.ACTIVE, false, null, Instant.now(), Instant.now(), null, null, null, null, null, false, false, false, null);
        when(employeeService.create(any(EmployeeRequest.class), eq("0001"))).thenReturn(createdEmployee);

        Employee employee = new Employee("0001", Salutation.MS, "Asha", "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1),
                MaritalStatus.SINGLE, "ABCDE1234F", "CPF00001", "asha.rao@example.com", "9876543210", LocalDate.of(2026, 1, 15),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);
        when(employeeRepository.getReferenceById(100L)).thenReturn(employee);

        OnboardingSubmitResponse response = onboardingService.finalizeOnboarding(DRAFT_ID);

        assertThat(response.employeeId()).isEqualTo(100L);
        assertThat(post.getVacancyStatus()).isEqualTo(VacancyStatus.OCCUPIED);
        verify(qualificationRepository).save(any());
        verify(pastServiceRecordRepository).save(any());
        verify(bankAccountRepository).save(any());
        verify(addressRepository, times(2)).save(any());
        verify(employmentCategoryRepository).save(any());
        verify(regularPayFixationRepository).save(any());
        verify(recruitmentDetailsRepository).save(any());
        verify(familyDetailsRepository).save(any());
        verify(dependentRepository).save(any());
        verify(nomineeRepository).save(any());
        verify(documentRepository).save(any());
        verify(postIncumbencyService).create(any());
        verify(draftRepository).delete(draft);
    }

    /** The onboarding wizard no longer collects the legacy Pay Scale dropdown - REGULAR must succeed on scaleCode alone (V52 loosened chk_regular_data to allow this). */
    @Test
    void finalizeOnboarding_regularWithoutPayScaleId_succeedsUsingScaleCodeOnly() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        OnboardingEmploymentStepRequest employmentWithoutPayScale = new OnboardingEmploymentStepRequest(
                EmploymentCategory.REGULAR, null, null, POST_ID, null, new BigDecimal("45000.00"),
                null, null, null, null, null, null, null, null, null, null, null, "E1", null,
                null, null, null, null,
                LocalDate.of(2026, 1, 15), 2026, RecruitmentMode.DIRECT_RECRUITMENT, "Written Test",
                null, null, "APT-001", LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 10));
        onboardingService.upsert(new OnboardingDraftUpsertRequest(
                DRAFT_ID, null, personal(), address(), null, true, banking(), List.of(), List.of(), employmentWithoutPayScale, family(), documents(), null), null);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", DEPARTMENT_ID);
        Designation designation = new Designation("Backend Developer");
        ReflectionTestUtils.setField(designation, "id", DESIGNATION_ID);
        PostMaster post = new PostMaster("POST-001", "Backend Developer", department, designation, true);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        post.setVacancyStatus(VacancyStatus.VACANT);
        post.setBudgeted(true);
        when(postMasterRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(gradeScaleMasterRepository.findByScaleCode("E1")).thenReturn(Optional.of(gradeScale()));

        EmployeeResponse createdEmployee = new EmployeeResponse(
                100L, "0001", "EMP000001", Salutation.MS, "Asha", null, "Rao", "Asha Rao", Gender.FEMALE,
                LocalDate.of(1990, 5, 1), MaritalStatus.SINGLE, null, "Indian", null, "ABCDE1234F", "XXXX-XXXX-9012",
                "asha.rao@example.com", null, "9876543210", null, LocalDate.of(2026, 1, 15),
                DEPARTMENT_ID, "Engineering", DESIGNATION_ID, "Backend Developer", null, null, null, null,
                null, null, EmployeeStatus.ACTIVE, false, null, Instant.now(), Instant.now(), null, null, null, null, null, false, false, false, null);
        when(employeeService.create(any(EmployeeRequest.class), eq("0001"))).thenReturn(createdEmployee);

        Employee employee = new Employee("0001", Salutation.MS, "Asha", "Rao", Gender.FEMALE, LocalDate.of(1990, 5, 1),
                MaritalStatus.SINGLE, "ABCDE1234F", "CPF00001", "asha.rao@example.com", "9876543210", LocalDate.of(2026, 1, 15),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);
        when(employeeRepository.getReferenceById(100L)).thenReturn(employee);

        OnboardingSubmitResponse response = onboardingService.finalizeOnboarding(DRAFT_ID);

        assertThat(response.employeeId()).isEqualTo(100L);
        ArgumentCaptor<EmployeeEmploymentCategory> captor = ArgumentCaptor.forClass(EmployeeEmploymentCategory.class);
        verify(employmentCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPayScale()).isNull();
        assertThat(captor.getValue().getGradeScale().getScaleCode()).isEqualTo("E1");
        assertThat(captor.getValue().getRegularBasicPay()).isEqualByComparingTo("45000.00");
        verifyNoInteractions(payScaleRepository);
    }

    @Test
    void finalizeOnboarding_regularWithoutPost_throwsBusinessRuleViolationException() {
        EmployeeOnboardingDraft draft = newDraft();
        stubFindDraft(draft);
        OnboardingEmploymentStepRequest incompleteEmployment = new OnboardingEmploymentStepRequest(
                EmploymentCategory.REGULAR, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null,
                LocalDate.of(2026, 1, 15), 2026, RecruitmentMode.DIRECT_RECRUITMENT, "Written Test",
                null, null, "APT-001", LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 10));
        onboardingService.upsert(new OnboardingDraftUpsertRequest(
                DRAFT_ID, null, personal(), address(), null, true, banking(), List.of(), List.of(), incompleteEmployment, family(), documents(), null), null);

        assertThatThrownBy(() -> onboardingService.finalizeOnboarding(DRAFT_ID))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("postId");
    }
}
