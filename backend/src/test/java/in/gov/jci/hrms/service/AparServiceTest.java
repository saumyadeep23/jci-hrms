package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.AparAcceptRequest;
import in.gov.jci.hrms.dto.EmployeeAparRequest;
import in.gov.jci.hrms.dto.EmployeeAparResponse;
import in.gov.jci.hrms.dto.ReportingAssessmentRequest;
import in.gov.jci.hrms.dto.RepresentationRequest;
import in.gov.jci.hrms.dto.ReviewingAssessmentRequest;
import in.gov.jci.hrms.dto.SelfAppraisalRequest;
import in.gov.jci.hrms.entity.AparCycle;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeApar;
import in.gov.jci.hrms.entity.EmployeeAparStatus;
import in.gov.jci.hrms.entity.FinalGrading;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.EmployeeAparRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AparServiceTest {

    private static final Long CYCLE_ID = 1L;
    private static final Long EMPLOYEE_ID = 2L;
    private static final Long REPORTING_OFFICER_ID = 3L;
    private static final Long REVIEWING_OFFICER_ID = 4L;
    private static final Long ACCEPTING_AUTHORITY_ID = 5L;
    private static final Long APAR_ID = 10L;

    @Mock
    private EmployeeAparRepository employeeAparRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AparCycleService aparCycleService;

    private AparService aparService;
    private AparCycle cycle;
    private Employee employee;
    private Employee reportingOfficer;
    private Employee reviewingOfficer;
    private Employee acceptingAuthority;

    @BeforeEach
    void setUp() {
        aparService = new AparService(employeeAparRepository, employeeRepository, aparCycleService);

        cycle = new AparCycle("2025-2026", LocalDate.of(2025, 4, 1), LocalDate.of(2026, 3, 31));
        ReflectionTestUtils.setField(cycle, "id", CYCLE_ID);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");

        employee = employeeWithId(EMPLOYEE_ID, "EMP-002", department, designation);
        reportingOfficer = employeeWithId(REPORTING_OFFICER_ID, "EMP-003", department, designation);
        reviewingOfficer = employeeWithId(REVIEWING_OFFICER_ID, "EMP-004", department, designation);
        acceptingAuthority = employeeWithId(ACCEPTING_AUTHORITY_ID, "EMP-005", department, designation);
    }

    private Employee employeeWithId(Long id, String code, Department department, Designation designation) {
        Employee e = new Employee(code, "First", "Last", code.toLowerCase() + "@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private EmployeeAparRequest validRequest() {
        return new EmployeeAparRequest(CYCLE_ID, EMPLOYEE_ID, REPORTING_OFFICER_ID, REVIEWING_OFFICER_ID, ACCEPTING_AUTHORITY_ID);
    }

    private EmployeeApar aparWith(EmployeeAparStatus status) {
        EmployeeApar apar = new EmployeeApar(cycle, employee, reportingOfficer, reviewingOfficer, acceptingAuthority);
        ReflectionTestUtils.setField(apar, "id", APAR_ID);
        apar.setStatus(status);
        return apar;
    }

    // ---- initiate ----

    @Test
    void initiate_createsInDraftStatus() {
        when(aparCycleService.findOrThrow(CYCLE_ID)).thenReturn(cycle);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(REPORTING_OFFICER_ID)).thenReturn(Optional.of(reportingOfficer));
        when(employeeRepository.findById(REVIEWING_OFFICER_ID)).thenReturn(Optional.of(reviewingOfficer));
        when(employeeRepository.findById(ACCEPTING_AUTHORITY_ID)).thenReturn(Optional.of(acceptingAuthority));
        when(employeeAparRepository.saveAndFlush(any(EmployeeApar.class))).thenAnswer(inv -> {
            EmployeeApar saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", APAR_ID);
            return saved;
        });

        EmployeeAparResponse response = aparService.initiate(validRequest());

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.DRAFT);
        assertThat(response.reportingOfficerId()).isEqualTo(REPORTING_OFFICER_ID);
    }

    @Test
    void initiate_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(aparCycleService.findOrThrow(CYCLE_ID)).thenReturn(cycle);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aparService.initiate(validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void initiate_whenCycleMissing_throwsMasterDataNotFoundException() {
        when(aparCycleService.findOrThrow(CYCLE_ID)).thenThrow(new MasterDataNotFoundException("APAR Cycle", CYCLE_ID));

        assertThatThrownBy(() -> aparService.initiate(validRequest()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void initiate_whenAlreadyExistsForCycleAndEmployee_throwsMasterDataConflictException() {
        when(aparCycleService.findOrThrow(CYCLE_ID)).thenReturn(cycle);
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(employeeRepository.findById(REPORTING_OFFICER_ID)).thenReturn(Optional.of(reportingOfficer));
        when(employeeRepository.findById(REVIEWING_OFFICER_ID)).thenReturn(Optional.of(reviewingOfficer));
        when(employeeRepository.findById(ACCEPTING_AUTHORITY_ID)).thenReturn(Optional.of(acceptingAuthority));
        when(employeeAparRepository.saveAndFlush(any(EmployeeApar.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> aparService.initiate(validRequest()))
                .isInstanceOf(MasterDataConflictException.class);
    }

    // ---- full lifecycle, one guarded transition at a time ----

    @Test
    void submitSelfAppraisal_fromDraft_movesToSubmittedByEmployee() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.DRAFT);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.submitSelfAppraisal(APAR_ID, new SelfAppraisalRequest("My self appraisal"));

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE);
        assertThat(response.selfAppraisalText()).isEqualTo("My self appraisal");
    }

    @Test
    void submitSelfAppraisal_whenNotDraft_throwsBusinessRuleViolationException() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        assertThatThrownBy(() -> aparService.submitSelfAppraisal(APAR_ID, new SelfAppraisalRequest("text")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submitReportingAssessment_fromSubmittedByEmployee_movesToReported() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.SUBMITTED_BY_EMPLOYEE);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.submitReportingAssessment(APAR_ID,
                new ReportingAssessmentRequest(new BigDecimal("8.5"), "Good performance"));

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.REPORTED);
        assertThat(response.reportingScore()).isEqualByComparingTo("8.5");
    }

    @Test
    void submitReportingAssessment_whenNotSubmittedByEmployee_throwsBusinessRuleViolationException() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.DRAFT);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        assertThatThrownBy(() -> aparService.submitReportingAssessment(APAR_ID,
                new ReportingAssessmentRequest(new BigDecimal("8.5"), "remarks")))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submitReviewingAssessment_fromReported_movesToReviewed() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.REPORTED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.submitReviewingAssessment(APAR_ID,
                new ReviewingAssessmentRequest(new BigDecimal("8.0"), "Concur with reporting officer"));

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.REVIEWED);
    }

    @Test
    void accept_fromReviewed_movesToAcceptedWithFinalScoreAndGrading() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.REVIEWED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.accept(APAR_ID,
                new AparAcceptRequest(new BigDecimal("8.25"), FinalGrading.VERY_GOOD));

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.ACCEPTED);
        assertThat(response.finalGrading()).isEqualTo(FinalGrading.VERY_GOOD);
    }

    @Test
    void disclose_fromAccepted_movesToDisclosed() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.ACCEPTED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.disclose(APAR_ID);

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.DISCLOSED);
    }

    @Test
    void submitRepresentation_fromDisclosed_movesToRepresentationSubmitted() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.DISCLOSED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.submitRepresentation(APAR_ID,
                new RepresentationRequest("I disagree with this grading"));

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.REPRESENTATION_SUBMITTED);
    }

    @Test
    void finalizeApar_fromDisclosed_withNoRepresentationFiled_succeeds() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.DISCLOSED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.finalizeApar(APAR_ID);

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.FINALIZED);
    }

    @Test
    void finalizeApar_fromRepresentationSubmitted_succeeds() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.REPRESENTATION_SUBMITTED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        EmployeeAparResponse response = aparService.finalizeApar(APAR_ID);

        assertThat(response.status()).isEqualTo(EmployeeAparStatus.FINALIZED);
    }

    @Test
    void finalizeApar_fromReported_throwsBusinessRuleViolationException() {
        EmployeeApar apar = aparWith(EmployeeAparStatus.REPORTED);
        when(employeeAparRepository.findById(APAR_ID)).thenReturn(Optional.of(apar));

        assertThatThrownBy(() -> aparService.finalizeApar(APAR_ID))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(employeeAparRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aparService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
