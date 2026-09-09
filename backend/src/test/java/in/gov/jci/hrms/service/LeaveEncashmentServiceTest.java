package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EncashmentGateDecisionRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentRequest;
import in.gov.jci.hrms.dto.LeaveEncashmentResponse;
import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EncashmentType;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.LeaveEncashmentApplication;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.InsufficientLeaveBalanceException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveEncashmentServiceTest {

    @Mock
    private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private EmployeeServiceBookRepository serviceBookRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private RegularPayFixationRepository regularPayFixationRepository;
    @Mock
    private DaRateHistoryRepository daRateHistoryRepository;

    private LeaveEncashmentService service;
    private Employee employee;
    private LeaveType elType;
    private LeaveEntitlementBalance balance;

    @BeforeEach
    void setUp() {
        service = new LeaveEncashmentService(encashmentRepository, entitlementBalanceRepository, leaveLedgerEntryRepository,
                serviceBookRepository, employeeRepository, leaveTypeRepository, regularPayFixationRepository, daRateHistoryRepository);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 10L);
        Designation designation = new Designation("Manager");
        ReflectionTestUtils.setField(designation, "id", 20L);
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);

        balance = new LeaveEntitlementBalance(employee, elType, LocalDate.now().getYear());
        balance.setEncashableAvailable(new BigDecimal("20.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        balance.setAvailableBalance(new BigDecimal("20.00"));
        balance.setCurrentBalance(new BigDecimal("20.00"));

        GradeScaleMaster gradeScale = new GradeScaleMaster("E2", Cadre.EXECUTIVE, 2, false,
                new BigDecimal("40000"), new BigDecimal("80000"));
        gradeScale.setScaleType(ScaleType.IDA);
        RegularPayFixation fixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("60000.00"), LocalDate.of(2020, 1, 1));
        DaRateHistory daRate = new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 4, 1), new BigDecimal("50.00"), true);

        lenient().when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        lenient().when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        lenient().when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(any(), any(), any()))
                .thenReturn(Optional.of(balance));
        lenient().when(entitlementBalanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(encashmentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L)).thenReturn(Optional.of(fixation));
        lenient().when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.of(daRate));
        lenient().when(serviceBookRepository.saveAndFlush(any())).thenAnswer(inv -> {
            var entry = inv.getArgument(0, in.gov.jci.hrms.entity.EmployeeServiceBook.class);
            ReflectionTestUtils.setField(entry, "id", 900L);
            return entry;
        });
    }

    @Test
    void apply_belowFifteenDaysForInServiceEl_throws() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("10.00"), null);
        assertThatThrownBy(() -> service.apply(request)).isInstanceOf(BusinessRuleViolationException.class);
    }

    // ---- once-per-calendar-year statutory rule (IN_SERVICE_EL only) ----

    @Test
    void apply_whenAnUnrejectedInServiceElClaimAlreadyExistsThisYear_throws() {
        LeaveEncashmentApplication existing = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(existing, "id", 42L);
        ReflectionTestUtils.setField(existing, "createdAt", java.time.Instant.now());
        when(encashmentRepository.findByEmployeeId(1L)).thenReturn(java.util.List.of(existing));

        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("15.00"), null);

        assertThatThrownBy(() -> service.apply(request))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("once in a calendar year")
                .hasMessageContaining("ELE-42");
    }

    @Test
    void apply_whenExistingClaimWasRejectedAtEitherGate_isNotCountedAndSucceeds() {
        LeaveEncashmentApplication hrRejected = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(hrRejected, "id", 41L);
        ReflectionTestUtils.setField(hrRejected, "createdAt", java.time.Instant.now());
        hrRejected.setHrApprovalStatus(ApprovalStatus.REJECTED);

        LeaveEncashmentApplication financeRejected = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(financeRejected, "id", 43L);
        ReflectionTestUtils.setField(financeRejected, "createdAt", java.time.Instant.now());
        financeRejected.setHrApprovalStatus(ApprovalStatus.APPROVED);
        financeRejected.setFinanceApprovalStatus(ApprovalStatus.REJECTED);

        when(encashmentRepository.findByEmployeeId(1L)).thenReturn(java.util.List.of(hrRejected, financeRejected));

        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("15.00"), null);

        assertThat(service.apply(request).elDaysClaimed()).isEqualByComparingTo("15.00");
    }

    @Test
    void apply_whenExistingClaimIsFromAPriorCalendarYear_isNotCountedAndSucceeds() {
        LeaveEncashmentApplication lastYear = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(lastYear, "id", 40L);
        ReflectionTestUtils.setField(lastYear, "createdAt", java.time.Instant.parse("2020-01-01T00:00:00Z"));
        when(encashmentRepository.findByEmployeeId(1L)).thenReturn(java.util.List.of(lastYear));

        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("15.00"), null);

        assertThat(service.apply(request).elDaysClaimed()).isEqualByComparingTo("15.00");
    }

    @Test
    void apply_supersannuationType_isExemptFromTheOncePerYearRuleEvenWithAnExistingInServiceClaim() {
        LeaveEncashmentApplication existing = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(existing, "id", 42L);
        ReflectionTestUtils.setField(existing, "createdAt", java.time.Instant.now());
        lenient().when(encashmentRepository.findByEmployeeId(1L)).thenReturn(java.util.List.of(existing));

        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.SUPERANNUATION, new BigDecimal("15.00"), null);

        assertThat(service.apply(request).encashmentType()).isEqualTo(EncashmentType.SUPERANNUATION);
    }

    @Test
    void apply_exceedingEncashableAvailable_throwsInsufficientBalance() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("25.00"), null);
        assertThatThrownBy(() -> service.apply(request)).isInstanceOf(InsufficientLeaveBalanceException.class);
    }

    @Test
    void apply_valid_reservesEncashableDays() {
        LeaveEncashmentRequest request = new LeaveEncashmentRequest(1L, EncashmentType.IN_SERVICE_EL, new BigDecimal("15.00"), null);

        LeaveEncashmentResponse response = service.apply(request);

        assertThat(balance.getEncashableReserved()).isEqualByComparingTo("15.00");
        assertThat(balance.getEncashableAvailable()).isEqualByComparingTo("5.00");
        // The top-level availableBalance must move with encashableAvailable on reservation - it used to
        // stay frozen here, leaving availableBalance ahead of encashableAvailable+enjoyableAvailable by
        // exactly the reserved amount for as long as the application stayed pending.
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("5.00");

        // CPSE formula: (60000 basic + 50% DA = 90000 monthly) / 30 * 15 days = 45000.00
        assertThat(response.daRateApplied()).isEqualByComparingTo("50.00");
        assertThat(response.grossAmount()).isEqualByComparingTo("45000.00");
        assertThat(response.arrearSettled()).isTrue();
    }

    @Test
    void financeApprove_beforeHrApproval_throws() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.financeApprove(5L, new EncashmentGateDecisionRequest(true, null), 99L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("HR");
    }

    @Test
    void financeApprove_afterHrApproval_debitsAndMarksPayrollEligibleAndWritesServiceBook() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        LeaveEncashmentResponse response = service.financeApprove(5L, new EncashmentGateDecisionRequest(true, "Approved"), 99L);

        assertThat(response.financeApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.payrollEligible()).isTrue();
        assertThat(response.serviceBookEntryId()).isEqualTo(900L);
        assertThat(balance.getEncashableCurrent()).isEqualByComparingTo("5.00");
        assertThat(balance.getEncashableEncashed()).isEqualByComparingTo("15.00");
    }

    @Test
    void financeApprove_writesServiceBookNarrativeWithGrossAmountWordingAndDdMmYyyyDate() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        application.setDaRateApplied(new BigDecimal("50.00"));
        application.setGrossAmount(new BigDecimal("45000.00"));
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        service.financeApprove(5L, new EncashmentGateDecisionRequest(true, "Approved"), 99L);

        org.mockito.ArgumentCaptor<in.gov.jci.hrms.entity.EmployeeServiceBook> captor =
                org.mockito.ArgumentCaptor.forClass(in.gov.jci.hrms.entity.EmployeeServiceBook.class);
        verify(serviceBookRepository).saveAndFlush(captor.capture());
        in.gov.jci.hrms.entity.EmployeeServiceBook entry = captor.getValue();

        String expectedDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        assertThat(entry.getRemarks())
                .contains("Gross Amount: Rs.45000.00")
                .contains("Sanction Ref: ELE-5 dated " + expectedDate + ".")
                .doesNotContain("Financial Involvement");
        assertThat(entry.getDaysEncashed()).isEqualByComparingTo("15.00");
        assertThat(entry.getEventDescription()).isNull();
    }

    @Test
    void hrReject_releasesReservation() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableAvailable(new BigDecimal("5.00"));
        balance.setAvailableBalance(new BigDecimal("5.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        service.hrApprove(5L, new EncashmentGateDecisionRequest(false, "Not eligible"), 99L);

        assertThat(balance.getEncashableReserved()).isEqualByComparingTo("0.00");
        assertThat(balance.getEncashableAvailable()).isEqualByComparingTo("20.00");
        assertThat(balance.getAvailableBalance()).isEqualByComparingTo("20.00");
    }

    @Test
    void financeApprove_whenDaRateHikedRetroactively_computesArrearAndFlagsUnsettled() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        application.setDaRateApplied(new BigDecimal("50.00")); // the rate quoted at apply() time
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));
        // DA has since been hiked to 55% with retroactive effect, ahead of Finance's decision.
        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any(), any()))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 7, 1), new BigDecimal("55.00"), true)));

        LeaveEncashmentResponse response = service.financeApprove(5L, new EncashmentGateDecisionRequest(true, "Approved"), 99L);

        // (60000 * 5% delta / 100) / 30 * 15 days = 1500.00
        assertThat(response.arrearAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.arrearSettled()).isFalse();
    }

    @Test
    void financeApprove_whenDaRateUnchanged_leavesArrearSettled() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        application.setHrApprovalStatus(ApprovalStatus.APPROVED);
        application.setDaRateApplied(new BigDecimal("50.00"));
        balance.setEncashableReserved(new BigDecimal("15.00"));
        balance.setEncashableCurrent(new BigDecimal("20.00"));
        when(encashmentRepository.findById(5L)).thenReturn(Optional.of(application));

        LeaveEncashmentResponse response = service.financeApprove(5L, new EncashmentGateDecisionRequest(true, "Approved"), 99L);

        assertThat(response.arrearAmount()).isEqualByComparingTo("0.00");
        assertThat(response.arrearSettled()).isTrue();
    }

    @Test
    void listByEmployee_returnsAllOfThatEmployeesApplications() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        when(encashmentRepository.findByEmployeeId(1L)).thenReturn(java.util.List.of(application));

        var results = service.listByEmployee(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(5L);
    }

    @Test
    void listHistory_includesOnlyFinalizedApplicationsInTheGivenYear() {
        LeaveEncashmentApplication sanctioned = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(sanctioned, "id", 5L);
        sanctioned.setHrApprovalStatus(ApprovalStatus.APPROVED);
        sanctioned.setFinanceApprovalStatus(ApprovalStatus.APPROVED);
        sanctioned.setFinanceApprovedAt(java.time.Instant.parse("2026-09-06T10:00:00Z"));

        LeaveEncashmentApplication stillPending = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(stillPending, "id", 6L);

        LeaveEncashmentApplication rejectedLastYear = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(rejectedLastYear, "id", 7L);
        rejectedLastYear.setHrApprovalStatus(ApprovalStatus.REJECTED);
        rejectedLastYear.setHrApprovedAt(java.time.Instant.parse("2025-03-01T10:00:00Z"));

        when(encashmentRepository.findAllWithEmployeeAndDesignation())
                .thenReturn(java.util.List.of(sanctioned, stillPending, rejectedLastYear));

        var results = service.listHistory(2026, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(5L);
        assertThat(results.get(0).status()).isEqualTo("SANCTIONED");
        assertThat(results.get(0).voucherRefNo()).isEqualTo("ELE-5");
    }

    @Test
    void listForAdminReview_enrichesWithEmployeeDetailsAndBatchedBasicPay() {
        LeaveEncashmentApplication application = new LeaveEncashmentApplication(employee, EncashmentType.IN_SERVICE_EL,
                new BigDecimal("15.00"), BigDecimal.ZERO);
        ReflectionTestUtils.setField(application, "id", 5L);
        when(encashmentRepository.findAllWithEmployeeAndDesignation()).thenReturn(java.util.List.of(application));

        in.gov.jci.hrms.entity.GradeScaleMaster gradeScale = new in.gov.jci.hrms.entity.GradeScaleMaster(
                "E2", in.gov.jci.hrms.entity.Cadre.EXECUTIVE, 2, false, new BigDecimal("40000"), new BigDecimal("80000"));
        in.gov.jci.hrms.entity.RegularPayFixation fixation = new in.gov.jci.hrms.entity.RegularPayFixation(
                employee, gradeScale, new BigDecimal("69920.00"), LocalDate.of(2020, 1, 1));
        when(regularPayFixationRepository.findByEmployeeIdInAndCurrentTrue(java.util.List.of(1L)))
                .thenReturn(java.util.List.of(fixation));

        var results = service.listForAdminReview();

        assertThat(results).hasSize(1);
        LeaveEncashmentResponse response = results.get(0);
        assertThat(response.fullName()).isEqualTo(employee.getFullName());
        assertThat(response.designation()).isEqualTo("Manager");
        assertThat(response.currentBasicPay()).isEqualByComparingTo("69920.00");
        assertThat(response.applicationDate()).isEqualTo(application.getCreatedAt());

        verify(regularPayFixationRepository, times(1)).findByEmployeeIdInAndCurrentTrue(any());
    }
}
