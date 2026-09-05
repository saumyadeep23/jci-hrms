package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.DisciplinaryCaseRequest;
import in.gov.jci.hrms.dto.DisciplinaryCaseResponse;
import in.gov.jci.hrms.dto.DisciplinaryStageUpdateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.DisciplinaryCase;
import in.gov.jci.hrms.entity.DisciplinaryCaseStatus;
import in.gov.jci.hrms.entity.DisciplinaryCaseType;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeServiceBook;
import in.gov.jci.hrms.entity.PenaltyType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.EmployeeNotFoundException;
import in.gov.jci.hrms.exception.MasterDataConflictException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DisciplinaryCaseRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisciplinaryServiceTest {

    private static final Long EMPLOYEE_ID = 1L;
    private static final Long INQUIRY_OFFICER_ID = 2L;
    private static final Long CASE_ID = 3L;

    @Mock
    private DisciplinaryCaseRepository disciplinaryCaseRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeServiceBookRepository employeeServiceBookRepository;

    private DisciplinaryService disciplinaryService;
    private Employee employee;
    private Employee inquiryOfficer;

    @BeforeEach
    void setUp() {
        disciplinaryService = new DisciplinaryService(disciplinaryCaseRepository, employeeRepository, employeeServiceBookRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);

        inquiryOfficer = new Employee("EMP-002", "Vikram", "Singh", "vikram.singh@example.com",
                LocalDate.of(2018, 1, 1), department, designation);
        ReflectionTestUtils.setField(inquiryOfficer, "id", INQUIRY_OFFICER_ID);
    }

    private DisciplinaryCase caseAt(DisciplinaryCaseStatus status) {
        DisciplinaryCase disciplinaryCase = new DisciplinaryCase("DC-2026-001", employee, DisciplinaryCaseType.CONDUCT_RULES);
        ReflectionTestUtils.setField(disciplinaryCase, "id", CASE_ID);
        disciplinaryCase.setStatus(status);
        return disciplinaryCase;
    }

    // ---- createCase ----

    @Test
    void createCase_withValidRequest_returnsInitiatedCase() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(disciplinaryCaseRepository.saveAndFlush(any(DisciplinaryCase.class))).thenAnswer(inv -> inv.getArgument(0));

        DisciplinaryCaseResponse response = disciplinaryService.createCase(
                new DisciplinaryCaseRequest("DC-2026-001", EMPLOYEE_ID, DisciplinaryCaseType.CONDUCT_RULES));

        assertThat(response.status()).isEqualTo(DisciplinaryCaseStatus.INITIATED);
        assertThat(response.caseNumber()).isEqualTo("DC-2026-001");
    }

    @Test
    void createCase_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disciplinaryService.createCase(
                new DisciplinaryCaseRequest("DC-2026-001", EMPLOYEE_ID, DisciplinaryCaseType.CONDUCT_RULES)))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    void createCase_whenCaseNumberAlreadyInUse_throwsMasterDataConflictException() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(disciplinaryCaseRepository.saveAndFlush(any(DisciplinaryCase.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> disciplinaryService.createCase(
                new DisciplinaryCaseRequest("DC-2026-001", EMPLOYEE_ID, DisciplinaryCaseType.CONDUCT_RULES)))
                .isInstanceOf(MasterDataConflictException.class);
    }

    // ---- updateStage: valid transitions ----

    @Test
    void updateStage_initiatedToChargeSheetIssued_setsChargeSheetDate() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.INITIATED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        DisciplinaryCaseResponse response = disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED, LocalDate.of(2026, 2, 1), null, null, null, null, null));

        assertThat(response.status()).isEqualTo(DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED);
        assertThat(response.chargeSheetDate()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    @Test
    void updateStage_chargeSheetIssuedWithoutDate_throwsBusinessRuleViolationException() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.INITIATED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        assertThatThrownBy(() -> disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateStage_chargeSheetIssuedToInquiryInProgress_resolvesInquiryOfficer() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.CHARGE_SHEET_ISSUED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));
        when(employeeRepository.findById(INQUIRY_OFFICER_ID)).thenReturn(Optional.of(inquiryOfficer));

        DisciplinaryCaseResponse response = disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.INQUIRY_IN_PROGRESS, null, INQUIRY_OFFICER_ID, null, null, null, null));

        assertThat(response.status()).isEqualTo(DisciplinaryCaseStatus.INQUIRY_IN_PROGRESS);
        assertThat(response.inquiryOfficerId()).isEqualTo(INQUIRY_OFFICER_ID);
    }

    @Test
    void updateStage_skippingStages_throwsBusinessRuleViolationException() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.INITIATED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        assertThatThrownBy(() -> disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.REPORT_SUBMITTED, null, null, null, null, null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateStage_fromClosed_isTerminal() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.CLOSED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        assertThatThrownBy(() -> disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.PENALTY_IMPOSED, null, null, PenaltyType.CENSURE, LocalDate.of(2026, 3, 1), null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- updateStage: PENALTY_IMPOSED / EXONERATED terminal branches ----

    @Test
    void updateStage_reportSubmittedToExonerated_doesNotTouchServiceBook() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.REPORT_SUBMITTED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        DisciplinaryCaseResponse response = disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.EXONERATED, null, null, null, null, null, "No wrongdoing found"));

        assertThat(response.status()).isEqualTo(DisciplinaryCaseStatus.EXONERATED);
        verify(employeeServiceBookRepository, never()).save(any());
    }

    @Test
    void updateStage_penaltyImposedWithoutPenaltyType_throwsBusinessRuleViolationException() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.REPORT_SUBMITTED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        assertThatThrownBy(() -> disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.PENALTY_IMPOSED, null, null, null, LocalDate.of(2026, 3, 1), null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(employeeServiceBookRepository, never()).save(any());
    }

    @Test
    void updateStage_penaltyImposedWithNonePenaltyType_throwsBusinessRuleViolationException() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.REPORT_SUBMITTED);
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        assertThatThrownBy(() -> disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.PENALTY_IMPOSED, null, null, PenaltyType.NONE, LocalDate.of(2026, 3, 1), null, null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void updateStage_penaltyImposed_generatesPunishmentServiceBookEntry() {
        DisciplinaryCase disciplinaryCase = caseAt(DisciplinaryCaseStatus.REPORT_SUBMITTED);
        disciplinaryCase.setChargeSheetDate(LocalDate.of(2026, 2, 1));
        when(disciplinaryCaseRepository.findById(CASE_ID)).thenReturn(Optional.of(disciplinaryCase));

        DisciplinaryCaseResponse response = disciplinaryService.updateStage(CASE_ID, new DisciplinaryStageUpdateRequest(
                DisciplinaryCaseStatus.PENALTY_IMPOSED, null, null, PenaltyType.WITHHOLDING_INCREMENT,
                LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1), "Withheld one increment"));

        assertThat(response.status()).isEqualTo(DisciplinaryCaseStatus.PENALTY_IMPOSED);
        assertThat(response.penaltyType()).isEqualTo(PenaltyType.WITHHOLDING_INCREMENT);

        ArgumentCaptor<EmployeeServiceBook> captor = ArgumentCaptor.forClass(EmployeeServiceBook.class);
        verify(employeeServiceBookRepository, times(1)).save(captor.capture());
        EmployeeServiceBook event = captor.getValue();
        assertThat(event.getEmployee().getId()).isEqualTo(EMPLOYEE_ID);
        assertThat(event.getEventType()).isEqualTo("PUNISHMENT");
        assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(event.getOrderNumber()).isEqualTo("DC-2026-001");
        assertThat(event.getOrderDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(event.isMigrated()).isFalse();
        assertThat(event.getEventDescription()).contains("WITHHOLDING_INCREMENT").contains("DC-2026-001");
    }

    // ---- getById / list / listByEmployee ----

    @Test
    void getById_whenMissing_throwsMasterDataNotFoundException() {
        when(disciplinaryCaseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> disciplinaryService.getById(99L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void listByEmployee_whenEmployeeMissing_throwsEmployeeNotFoundException() {
        when(employeeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> disciplinaryService.listByEmployee(99L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }
}
