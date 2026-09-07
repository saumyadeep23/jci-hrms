package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeMovementRecordResponse;
import in.gov.jci.hrms.dto.JoiningDecisionRequest;
import in.gov.jci.hrms.dto.JoiningReportRequest;
import in.gov.jci.hrms.dto.MovementReleaseRequest;
import in.gov.jci.hrms.dto.PostIncumbencyRequest;
import in.gov.jci.hrms.dto.ServiceBookEventRequest;
import in.gov.jci.hrms.entity.CareerEventType;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.JoiningStatus;
import in.gov.jci.hrms.entity.LeaveEntitlementBalance;
import in.gov.jci.hrms.entity.LeaveType;
import in.gov.jci.hrms.entity.AssignmentType;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.MovementStatus;
import in.gov.jci.hrms.entity.PayrollSyncStatus;
import in.gov.jci.hrms.entity.PostMaster;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.entity.TransferNature;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.LeaveEntitlementBalanceRepository;
import in.gov.jci.hrms.repository.LeaveLedgerEntryRepository;
import in.gov.jci.hrms.repository.LeaveTypeRepository;
import in.gov.jci.hrms.repository.PostIncumbencyRepository;
import in.gov.jci.hrms.repository.PostMasterRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JoiningReportServiceTest {

    @Mock
    private EmployeeMovementRecordRepository movementRecordRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private DbClockService dbClockService;
    @Mock
    private EmployeeServiceBookService employeeServiceBookService;
    @Mock
    private PayrollMovementIntegrationService payrollMovementIntegrationService;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private LeaveEntitlementBalanceRepository entitlementBalanceRepository;
    @Mock
    private LeaveLedgerEntryRepository leaveLedgerEntryRepository;
    @Mock
    private PostIncumbencyRepository postIncumbencyRepository;
    @Mock
    private PostMasterRepository postMasterRepository;
    @Mock
    private PostIncumbencyService postIncumbencyService;

    private JoiningReportService service;
    private Employee employee;
    private RegionalOffice fromOffice;
    private RegionalOffice toOffice;
    private Designation designation;
    private LeaveType elType;

    @BeforeEach
    void setUp() {
        service = new JoiningReportService(movementRecordRepository, employeeRepository, dbClockService,
                new JoiningTimeCalculatorService(), new GeofenceService(), employeeServiceBookService,
                payrollMovementIntegrationService, leaveTypeRepository, entitlementBalanceRepository, leaveLedgerEntryRepository,
                new in.gov.jci.hrms.service.pdf.ReleaseOrderPdfGenerator(new in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper()),
                postIncumbencyRepository, postMasterRepository, postIncumbencyService);

        Department department = new Department("ENG", "Engineering");
        designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com", LocalDate.of(2015, 1, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);

        fromOffice = new RegionalOffice("RO-A", "Kolkata RO", "West Bengal", CityClass.Y, true);
        ReflectionTestUtils.setField(fromOffice, "id", 10L);
        toOffice = new RegionalOffice("RO-B", "Mumbai RO", "Maharashtra", CityClass.X, true);
        ReflectionTestUtils.setField(toOffice, "id", 20L);
        toOffice.setLatitude(new BigDecimal("19.076000"));
        toOffice.setLongitude(new BigDecimal("72.877700"));
        toOffice.setGeofenceRadiusMeters(new BigDecimal("100.00"));

        elType = new LeaveType("EL", "Earned Leave", new BigDecimal("30.0"), true, true);
        ReflectionTestUtils.setField(elType, "id", 2L);

        lenient().when(payrollMovementIntegrationService.generateInputs(any())).thenReturn(List.of());
        lenient().when(entitlementBalanceRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(leaveLedgerEntryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private EmployeeMovementRecord newRecord(MovementOrderType orderType, TransferNature nature, boolean benefitAdmissible) {
        MovementOrder order = new MovementOrder(orderType, "JCI/Order/2026/01", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(order, "id", 100L);
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        ReflectionTestUtils.setField(record, "id", 500L);
        record.setTransferNature(nature);
        record.setTransferBenefitAdmissible(benefitAdmissible);
        record.setMovementStatus(MovementStatus.RELIEVED);
        record.setReleaseDate(LocalDate.of(2026, 3, 10));
        record.setReleaseSession(SessionType.AFTERNOON);
        record.setAdmissibleJtDays(10);
        when(movementRecordRepository.findById(500L)).thenReturn(Optional.of(record));
        return record;
    }

    // ---- Database-Clock FN/AN session evaluation ----

    @Test
    void submitJoiningReport_dbClockBefore13IST_evaluatesForenoon() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        // 2026-03-20T05:50:00Z = 11:20 IST
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-20T05:50:00Z"));

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningSession()).isEqualTo(SessionType.FORENOON);
        assertThat(response.joiningDate()).isEqualTo(LocalDate.of(2026, 3, 20));
    }

    @Test
    void submitJoiningReport_dbClockExactly13IST_evaluatesAfternoon() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        // 2026-03-20T07:30:00Z = 13:00:00 IST exactly - the >= boundary lands on AFTERNOON.
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-20T07:30:00Z"));

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningSession()).isEqualTo(SessionType.AFTERNOON);
    }

    @Test
    void submitJoiningReport_dbClockOneMinuteBefore13IST_evaluatesForenoon() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        // 2026-03-20T07:29:00Z = 12:59 IST
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-20T07:29:00Z"));

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningSession()).isEqualTo(SessionType.FORENOON);
    }

    @Test
    void submitJoiningReport_dbClockLateAfternoonIST_evaluatesAfternoon() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        // 2026-03-20T09:00:00Z = 14:30 IST
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-20T09:00:00Z"));

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningSession()).isEqualTo(SessionType.AFTERNOON);
    }

    @Test
    void submitJoiningReport_selfServiceGuard_rejectsSubmissionForAnotherEmployee() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);

        assertThatThrownBy(() -> service.submitJoiningReport(500L, joiningRequest(), 999L, "203.0.113.5"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submitJoiningReport_notYetRelieved_throws() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setMovementStatus(MovementStatus.ORDERED);

        assertThatThrownBy(() -> service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void submitJoiningReport_computesAvailedUnavailedAndExcessTransitDays() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true); // releaseDate = 2026-03-10, admissible = 10
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-16T05:50:00Z")); // joins 2026-03-16 -> 6 calendar days later

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningTimeAvailedDays()).isEqualTo(6);
        assertThat(response.unavailedJtDays()).isEqualTo(4); // 10 - 6
        assertThat(response.excessTransitLwpDays()).isZero();
    }

    @Test
    void submitJoiningReport_availedExceedsAdmissible_flagsExcessTransitLwp() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true); // releaseDate = 2026-03-10, admissible = 10
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-25T05:50:00Z")); // joins 2026-03-25 -> 15 calendar days later

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.5");

        assertThat(response.joiningTimeAvailedDays()).isEqualTo(15);
        assertThat(response.unavailedJtDays()).isZero();
        assertThat(response.excessTransitLwpDays()).isEqualTo(5); // 15 - 10
    }

    // ---- Soft GPS verification ----

    @Test
    void submitJoiningReport_coordinatesWithinGeofence_setsGeoVerifiedTrue() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-16T05:50:00Z"));
        JoiningReportRequest request = new JoiningReportRequest("JRN-001", new BigDecimal("19.076000"), new BigDecimal("72.877700"),
                new BigDecimal("12.0"), null);

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, request, 1L, "203.0.113.5");

        assertThat(response.geoVerified()).isTrue();
    }

    @Test
    void submitJoiningReport_coordinatesOutsideGeofence_setsGeoVerifiedFalseButStillSubmits() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-16T05:50:00Z"));
        // Delhi coordinates - far outside the 100m Mumbai RO geofence, but submission still succeeds (soft check).
        JoiningReportRequest request = new JoiningReportRequest("JRN-001", new BigDecimal("28.613900"), new BigDecimal("77.209000"),
                new BigDecimal("12.0"), null);

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, request, 1L, "203.0.113.5");

        assertThat(response.geoVerified()).isFalse();
        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.PENDING_VERIFICATION);
    }

    @Test
    void submitJoiningReport_noCoordinatesSupplied_softlyMarksUnverifiedWithoutBlocking() {
        newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-16T05:50:00Z"));
        JoiningReportRequest request = new JoiningReportRequest("JRN-001", null, null, null, null);

        EmployeeMovementRecordResponse response = service.submitJoiningReport(500L, request, 1L, "203.0.113.5");

        assertThat(response.geoVerified()).isFalse();
        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.PENDING_VERIFICATION);
    }

    // ---- Release ----

    @Test
    void release_writesTransferReleaseServiceBookEntry() {
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Order/2026/01", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(order, "id", 100L);
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        ReflectionTestUtils.setField(record, "id", 500L);
        when(movementRecordRepository.findById(500L)).thenReturn(Optional.of(record));
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-10T08:00:00Z"));

        service.release(500L, new MovementReleaseRequest("REL/2026/01", LocalDate.of(2026, 3, 10), SessionType.AFTERNOON));

        assertThat(record.getMovementStatus()).isEqualTo(MovementStatus.RELIEVED);
        ArgumentCaptor<ServiceBookEventRequest> captor = ArgumentCaptor.forClass(ServiceBookEventRequest.class);
        verify(employeeServiceBookService).recordEvent(eq(1L), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(CareerEventType.TRANSFER_RELEASE);
    }

    @Test
    void release_closesTheEmployeesActiveIncumbencyButNeverTouchesTheEmployeesOwnRecord() {
        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER, "JCI/Order/2026/01", LocalDate.of(2026, 3, 1));
        ReflectionTestUtils.setField(order, "id", 100L);
        EmployeeMovementRecord record = new EmployeeMovementRecord(order, employee, fromOffice, designation, toOffice, designation);
        ReflectionTestUtils.setField(record, "id", 500L);
        when(movementRecordRepository.findById(500L)).thenReturn(Optional.of(record));
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-10T08:00:00Z"));

        service.release(500L, new MovementReleaseRequest("REL/2026/01", LocalDate.of(2026, 3, 10), SessionType.AFTERNOON));

        // Closes the OUTGOING post's incumbency (keyed by employee, since the movement record itself
        // carries no postId) - but the CRITICAL INVARIANT is that release() never writes to employees
        // itself: the employee is still in transit and does not yet hold the new post.
        verify(postIncumbencyRepository).closeActiveIncumbency(1L, LocalDate.of(2026, 3, 10));
        verify(employeeRepository, never()).save(any());
    }

    // ---- Post Incumbency lifecycle + master-data sync on joining approval ----

    @Test
    void decide_approve_exactlyOneMatchingPost_createsSubstantiveIncumbencyAndSyncsEmployeeMasterData() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setJoiningReportNo("JRN-001");
        record.setUnavailedJtDays(0);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);
        Department toDepartment = new Department("SALES", "Sales");
        ReflectionTestUtils.setField(toDepartment, "id", 77L);
        ReflectionTestUtils.setField(record, "toDepartment", toDepartment);
        ReflectionTestUtils.setField(designation, "id", 5L);

        PostMaster post = new PostMaster("PC-100", "Regional Sales Manager", toDepartment, designation, true);
        ReflectionTestUtils.setField(post, "id", 900L);
        post.setRegionalOffice(toOffice);
        when(postMasterRepository.findByDepartment_IdAndDesignation_IdAndRegionalOffice_Id(77L, 5L, 20L))
                .thenReturn(List.of(post));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        service.decide(500L, new JoiningDecisionRequest(true, null), null);

        verify(postIncumbencyRepository).closeActiveIncumbency(1L, LocalDate.of(2026, 3, 16));
        ArgumentCaptor<PostIncumbencyRequest> requestCaptor = ArgumentCaptor.forClass(PostIncumbencyRequest.class);
        verify(postIncumbencyService).create(requestCaptor.capture());
        PostIncumbencyRequest captured = requestCaptor.getValue();
        assertThat(captured.postId()).isEqualTo(900L);
        assertThat(captured.employeeId()).isEqualTo(1L);
        assertThat(captured.assignmentType()).isEqualTo(AssignmentType.SUBSTANTIVE);
        assertThat(captured.startDate()).isEqualTo(LocalDate.of(2026, 3, 16));
        assertThat(captured.orderReference()).isEqualTo("JRN-001");

        assertThat(employee.getDesignation()).isEqualTo(designation);
        assertThat(employee.getDepartment()).isEqualTo(toDepartment);
        assertThat(employee.getRegionalOffice()).isEqualTo(toOffice);
        verify(employeeRepository).save(employee);
    }

    @Test
    void decide_approve_ambiguousPostMatch_skipsIncumbencySyncWithoutFailingApproval() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(0);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);
        Department toDepartment = new Department("SALES", "Sales");
        ReflectionTestUtils.setField(toDepartment, "id", 77L);
        ReflectionTestUtils.setField(record, "toDepartment", toDepartment);
        ReflectionTestUtils.setField(designation, "id", 5L);
        // Zero matching sanctioned posts for this destination - nothing unambiguous to link/sync.
        when(postMasterRepository.findByDepartment_IdAndDesignation_IdAndRegionalOffice_Id(77L, 5L, 20L))
                .thenReturn(List.of());

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(true, null), null);

        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.ACCEPTED); // approval itself still succeeds
        verify(postIncumbencyService, never()).create(any());
        verify(employeeRepository, never()).save(any());
    }

    // ---- 300-day EL ceiling on approval ----

    @Test
    void decide_approve_administrativeTransferWithBenefit_creditsUnavailedJtToEl() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setJoiningTimeAvailedDays(6);
        record.setUnavailedJtDays(4);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));
        when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 2L, 2026)).thenReturn(Optional.empty());

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(true, null), null);

        assertThat(response.elCredited()).isTrue();
        assertThat(response.elCreditedDays()).isEqualTo(4);
        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.ACCEPTED);
        assertThat(response.movementStatus()).isEqualTo(MovementStatus.RELIEVED); // unchanged by decide() itself
        assertThat(response.payrollSyncStatus()).isEqualTo(PayrollSyncStatus.LPC_ISSUED);
        assertThat(response.lpcNumber()).isNotBlank();

        ArgumentCaptor<LeaveEntitlementBalance> balanceCaptor = ArgumentCaptor.forClass(LeaveEntitlementBalance.class);
        verify(entitlementBalanceRepository, atLeastOnce()).saveAndFlush(balanceCaptor.capture());
        LeaveEntitlementBalance savedBalance = balanceCaptor.getValue();
        // The 50:50 encashable/enjoyable split must move in lockstep with currentBalance/availableBalance -
        // this used to be skipped entirely here, leaving the total ahead of encashableCurrent+enjoyableCurrent.
        assertThat(savedBalance.getCurrentBalance())
                .isEqualByComparingTo(savedBalance.getEncashableCurrent().add(savedBalance.getEnjoyableCurrent()));
        assertThat(savedBalance.getAvailableBalance())
                .isEqualByComparingTo(savedBalance.getEncashableAvailable().add(savedBalance.getEnjoyableAvailable()));

        ArgumentCaptor<ServiceBookEventRequest> captor = ArgumentCaptor.forClass(ServiceBookEventRequest.class);
        verify(employeeServiceBookService, times(2)).recordEvent(eq(1L), captor.capture()); // TRANSFER_JOINING + TRANSFER_BENEFIT_EL_CREDIT
        List<CareerEventType> eventTypes = captor.getAllValues().stream().map(ServiceBookEventRequest::eventType).toList();
        assertThat(eventTypes).containsExactly(CareerEventType.TRANSFER_JOINING, CareerEventType.TRANSFER_BENEFIT_EL_CREDIT);
    }

    @Test
    void decide_approve_creditCappedAt300DayCeiling() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(10);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));

        LeaveEntitlementBalance nearCeiling = new LeaveEntitlementBalance(employee, elType, 2026);
        nearCeiling.setCurrentBalance(new BigDecimal("297.00")); // only 3 days of room left before the 300-day ceiling
        when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 2L, 2026)).thenReturn(Optional.of(nearCeiling));

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(true, null), null);

        assertThat(response.elCreditedDays()).isEqualTo(3); // capped, not the full 10
        assertThat(nearCeiling.getCurrentBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void decide_approve_atCeilingAlready_creditsNothing() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(4);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);
        when(leaveTypeRepository.findByCode("EL")).thenReturn(Optional.of(elType));

        LeaveEntitlementBalance atCeiling = new LeaveEntitlementBalance(employee, elType, 2026);
        atCeiling.setCurrentBalance(new BigDecimal("300.00"));
        when(entitlementBalanceRepository.findByEmployeeIdAndLeaveTypeIdAndYear(1L, 2L, 2026)).thenReturn(Optional.of(atCeiling));

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(true, null), null);

        assertThat(response.elCredited()).isFalse();
        assertThat(response.elCreditedDays()).isZero();
        verify(leaveLedgerEntryRepository, never()).saveAndFlush(any());
        // Only the TRANSFER_JOINING event fires - no TRANSFER_BENEFIT_EL_CREDIT entry when nothing was credited.
        verify(employeeServiceBookService, times(1)).recordEvent(anyLong(), any());
    }

    @Test
    void decide_approve_ownRequestTransfer_neverCreditsEl() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.OWN_REQUEST, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(0); // OWN_REQUEST never has admissible JT to begin with
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        service.decide(500L, new JoiningDecisionRequest(true, null), null);

        verify(entitlementBalanceRepository, never()).findByEmployeeIdAndLeaveTypeIdAndYear(any(), any(), any());
        verify(leaveLedgerEntryRepository, never()).saveAndFlush(any());
    }

    @Test
    void decide_approve_benefitNotAdmissible_neverCreditsElEvenIfAdministrative() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, false);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(4);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        service.decide(500L, new JoiningDecisionRequest(true, null), null);

        verify(entitlementBalanceRepository, never()).findByEmployeeIdAndLeaveTypeIdAndYear(any(), any(), any());
    }

    // ---- Promotion service-book automation ----

    @Test
    void decide_approve_promotionOrder_recordsPromotionAndPayFixationEvents() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.PROMOTION, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(0);
        record.setProbationPeriodMonths(6);
        record.setPromotionalBasicPay(new BigDecimal("65000.00"));
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(true, null), null);

        ArgumentCaptor<ServiceBookEventRequest> captor = ArgumentCaptor.forClass(ServiceBookEventRequest.class);
        verify(employeeServiceBookService, times(3)).recordEvent(eq(1L), captor.capture()); // JOINING + PROMOTION + PAY_FIXATION
        List<CareerEventType> eventTypes = captor.getAllValues().stream().map(ServiceBookEventRequest::eventType).toList();
        assertThat(eventTypes).containsExactly(CareerEventType.TRANSFER_JOINING, CareerEventType.PROMOTION, CareerEventType.PAY_FIXATION);
        assertThat(response.probationEndDate()).isEqualTo(LocalDate.of(2026, 9, 16));
    }

    @Test
    void decide_reject_setsRejectedStatusAndSkipsAllApprovalSideEffects() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningDate(LocalDate.of(2026, 3, 16));
        record.setJoiningSession(SessionType.FORENOON);
        record.setUnavailedJtDays(4);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        EmployeeMovementRecordResponse response = service.decide(500L, new JoiningDecisionRequest(false, "GPS mismatch"), null);

        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.REJECTED);
        verify(employeeServiceBookService, never()).recordEvent(any(), any());
        verify(payrollMovementIntegrationService, never()).generateInputs(any());
    }

    // ---- Clarification / re-submission lifecycle ----

    @Test
    void requestClarification_pendingVerification_setsClarificationRequestedAndSavesRemarks() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        EmployeeMovementRecordResponse response = service.requestClarification(500L, "GPS coordinates do not match the destination office", 2L);

        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.CLARIFICATION_REQUESTED);
        assertThat(record.getClarificationRemarks()).isEqualTo("GPS coordinates do not match the destination office");
        assertThat(record.getClarificationRequestedAt()).isNotNull();
    }

    @Test
    void requestClarification_blankRemarks_throws() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> service.requestClarification(500L, "   ", 2L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void requestClarification_notPendingVerification_throws() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.NOT_SUBMITTED);

        assertThatThrownBy(() -> service.requestClarification(500L, "Please clarify", 2L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void resubmitJoiningReport_clarificationRequested_returnsToPendingVerificationWithFreshSessionEval() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.CLARIFICATION_REQUESTED);
        record.setClarificationRemarks("GPS coordinates do not match");
        record.setJoiningReportNo("JRN-001");
        // 2026-03-21T09:00:00Z = 14:30 IST -> a different (later) session than the original submission would have gotten.
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-21T09:00:00Z"));

        EmployeeMovementRecordResponse response = service.resubmitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.9");

        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.PENDING_VERIFICATION);
        assertThat(response.joiningSession()).isEqualTo(SessionType.AFTERNOON);
        assertThat(response.joiningDate()).isEqualTo(LocalDate.of(2026, 3, 21));
    }

    @Test
    void resubmitJoiningReport_incrementsResubmissionCountAndSetsResubmittedAt() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.CLARIFICATION_REQUESTED);
        record.setJoiningReportNo("JRN-001");
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-21T05:50:00Z"));

        service.resubmitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.9");

        assertThat(record.getResubmissionCount()).isEqualTo(1);
        assertThat(record.getResubmittedAt()).isNotNull();
    }

    @Test
    void resubmitJoiningReport_notClarificationRequested_throws() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> service.resubmitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.9"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void resubmitJoiningReport_selfServiceGuard_rejectsAnotherEmployee() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.CLARIFICATION_REQUESTED);

        assertThatThrownBy(() -> service.resubmitJoiningReport(500L, joiningRequest(), 999L, "203.0.113.9"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void resubmitJoiningReport_sameJoiningReportNumber_isAllowed() {
        EmployeeMovementRecord record = newRecord(MovementOrderType.TRANSFER, TransferNature.ADMINISTRATIVE, true);
        record.setJoiningStatus(JoiningStatus.CLARIFICATION_REQUESTED);
        record.setJoiningReportNo("JRN-001"); // same number the resubmission payload also uses - must not collide with itself
        when(dbClockService.now()).thenReturn(Instant.parse("2026-03-21T05:50:00Z"));

        EmployeeMovementRecordResponse response = service.resubmitJoiningReport(500L, joiningRequest(), 1L, "203.0.113.9");

        assertThat(response.joiningStatus()).isEqualTo(JoiningStatus.PENDING_VERIFICATION);
    }

    private JoiningReportRequest joiningRequest() {
        return new JoiningReportRequest("JRN-001", null, null, null, "Reported for duty");
    }
}
