package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.ApprovalStatus;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CityClass;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMovementInput;
import in.gov.jci.hrms.entity.PtaxSlab;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.StateMaster;
import in.gov.jci.hrms.entity.StateType;
import in.gov.jci.hrms.entity.TransportAllowanceRate;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DailyAttendanceRepository;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import in.gov.jci.hrms.repository.LeaveEncashmentApplicationRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.PayrollMovementInputRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.EmployeeCeaClaimRepository;
import in.gov.jci.hrms.repository.PayrollTaxOverrideRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.TransportAllowanceRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Worked example: employee 2801, Basic 61,800 at Kolkata HO (X-class), promoted (Basic -&gt; 70,000)
 * AND transferred to Guwahati RO (Y-class, a remote-area state) with effect from 14-Sep-2026 (AN);
 * released from Kolkata 12-Sep-2026 (AN), joined Guwahati 14-Sep-2026 (AN). Pins down the exact
 * September-2026 gross-salary/P-Tax split PayrollBatchComputationService computes for a same-month
 * promotion + transfer, using the real day-split formulas from PayrollMovementIntegrationService
 * (releasingOfficeDays/receivingOfficeDays) and RegularPayFixation's plain-LocalDate effective-date
 * semantics (a promotion's effective date wins the WHOLE of its effective day, including a still-in-
 * transit day - see class javadoc).
 *
 * <p>Day accounting for September 2026 (30 days), per PayrollMovementIntegrationService's own rules:
 * <ul>
 *   <li>Released 12-Sep (AN) -&gt; releasingOfficeDays = 12 (Kolkata pays through the whole of the 12th).</li>
 *   <li>Joined 14-Sep (AN) -&gt; receivingOfficeDays = 16 (Guwahati pays from the 15th).</li>
 *   <li>The remaining 2 days (13th-14th) are Joining Time, fully within admissible limits for a
 *   Kolkata-Guwahati transfer -&gt; transitJtDays = 2, transitLwpDays = 0, paid (HRA) at the RELEASING
 *   office's terms per FR/SR convention, but with NO Transport Allowance (a commute allowance, which a
 *   still-in-transit day earns none of - see PayrollBatchComputationService's own javadoc).</li>
 * </ul>
 * The promotion's RegularPayFixation effective date (14-Sep) lands exactly on the second Joining Time
 * day, so that single day is paid at the NEW basic even though the employee is still technically in
 * transit that day - a plain LocalDate effective date has no AN/FN granularity to split within a day.
 *
 * <p>Rates below are the REAL values read from the local dev database (docker container
 * jci-hrms-local-db) as of this writing, not illustrative guesses: IDA DA 55.70% (da_rate_history,
 * effective_from 2026-07-01, still the latest row), HRA 30%/20% for X/Y and Transport base 7200/3600
 * for grade scales E3 and E4 alike (payroll_hra_rates / payroll_transport_allowance_rates), Assam's
 * Remote Area Allowance 10% (state_master), and the real West Bengal / Assam P-Tax slabs
 * (payroll_ptax_slabs). Employee 2801 (id 8, "SAUMYADEEP GHOSH") is a real employee in that database,
 * currently on scale E3 at exactly Basic 61,800 (regular_pay_fixations, effective_from 2026-03-23) at
 * Kolkata HO (ro_master id 2, West Bengal, X) - matching this scenario's "before" state exactly. E4's
 * minimum_basic is exactly 70,000 (grade_scale_master), which is why a promotion fixed at that
 * DPE-3%-then-clamped-to-scale-minimum figure lands on precisely the "new basic is 70000" the scenario
 * states - see MovementOrderService.applyPromotionPayFixation(). No movement/promotion has actually
 * been entered for this employee in the database yet - that part of the scenario is hypothetical, fed
 * in here as test fixtures.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MidMonthPromotionTransferTransitPayrollTest {

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_DA_IDA = 8;
    private static final int HEAD_HRA = 9;
    private static final int HEAD_TRANSPORT = 10;
    private static final int HEAD_REMOTE_AREA = 21;
    private static final int HEAD_CPF = 27;
    private static final int HEAD_PTAX = 49;

    @Mock private PayrollBatchRepository payrollBatchRepository;
    @Mock private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Mock private PayrollMonthlyHeadItemRepository payrollMonthlyHeadItemRepository;
    @Mock private PayrollMonthlyStatutoryItemRepository payrollMonthlyStatutoryItemRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;
    @Mock private DaRateHistoryRepository daRateHistoryRepository;
    @Mock private DailyAttendanceRepository dailyAttendanceRepository;
    @Mock private PayrollHraRateRepository payrollHraRateRepository;
    @Mock private TransportAllowanceRateRepository transportAllowanceRateRepository;
    @Mock private PtaxSlabRepository ptaxSlabRepository;
    @Mock private StateMasterRepository stateMasterRepository;
    @Mock private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;
    @Mock private EmployeeNpsDeclarationRepository npsDeclarationRepository;
    @Mock private EmployeeVehicleAllotmentRepository vehicleAllotmentRepository;
    @Mock private EmployeeQuarterAllotmentService quarterAllotmentService;
    @Mock private LeaveEncashmentApplicationRepository encashmentRepository;
    @Mock private PayrollMovementInputRepository payrollMovementInputRepository;
    @Mock private PayrollTdsEngine payrollTdsEngine;
    @Mock private PayrollTaxOverrideRepository payrollTaxOverrideRepository;
    @Mock private EmployeeCeaClaimRepository ceaClaimRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeSuspensionRecordRepository suspensionRecordRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeSuspensionNecRepository suspensionNecRepository;
    @Mock private in.gov.jci.hrms.repository.EmployeeDeputationRecordRepository deputationRecordRepository;

    private PayrollBatchComputationService service;

    private PayrollBatch batch;
    private Employee employee;
    private RegionalOffice kolkataHo;
    private RegionalOffice guwahatiRo;
    private RegularPayFixation oldFixation;
    private RegularPayFixation newFixation;

    @BeforeEach
    void setUp() {
        service = new PayrollBatchComputationService(payrollBatchRepository, payrollMonthlyRecordRepository,
                payrollMonthlyHeadItemRepository, payrollMonthlyStatutoryItemRepository, employeeRepository, regularPayFixationRepository, daRateHistoryRepository,
                dailyAttendanceRepository, payrollHraRateRepository, transportAllowanceRateRepository, ptaxSlabRepository,
                stateMasterRepository, payrollStatutoryParameterRepository, npsDeclarationRepository, vehicleAllotmentRepository,
                quarterAllotmentService, encashmentRepository, payrollMovementInputRepository, payrollTdsEngine, payrollTaxOverrideRepository, ceaClaimRepository,
                suspensionRecordRepository, suspensionNecRepository, deputationRecordRepository);

        batch = new PayrollBatch("BATCH-2026-09", 9, 2026, "2026-2027");
        ReflectionTestUtils.setField(batch, "id", 900L);
        batch.setStatus(PayrollBatchStatus.DRAFT);

        Department department = new Department("ADM", "Administration");
        Designation designation = new Designation("Senior Manager");
        kolkataHo = new RegionalOffice("KOL-HO", "Kolkata Head Office", "West Bengal", CityClass.X, true);
        guwahatiRo = new RegionalOffice("GUW-RO", "Guwahati Regional Office", "Assam", CityClass.Y, true);

        employee = new Employee("2801", "Ravi", "Sharma", "ravi.sharma@example.com",
                LocalDate.of(2015, 6, 1), department, designation);
        ReflectionTestUtils.setField(employee, "id", 2801L);
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setRegionalOffice(kolkataHo); // never updated by the movement lifecycle - see class/service javadoc

        GradeScaleMaster gradeScale = new GradeScaleMaster("E4", Cadre.EXECUTIVE, 4, false,
                new BigDecimal("55000.00"), new BigDecimal("95000.00"));
        ReflectionTestUtils.setField(gradeScale, "id", 40L);
        gradeScale.setScaleType(ScaleType.IDA);

        oldFixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("61800.00"), LocalDate.of(2020, 1, 1));
        oldFixation.setEffectiveTo(LocalDate.of(2026, 9, 13));
        oldFixation.setCurrent(false);

        newFixation = new RegularPayFixation(employee, gradeScale, new BigDecimal("70000.00"), LocalDate.of(2026, 9, 14));
        // effectiveTo stays null (open/current) - matches MovementOrderService.applyPromotionPayFixation()'s own shape.

        MovementOrder order = new MovementOrder(MovementOrderType.TRANSFER_CUM_PROMOTION, "ORD/2801/2026", LocalDate.of(2026, 8, 20));
        EmployeeMovementRecord movement = new EmployeeMovementRecord(order, employee, kolkataHo, designation, guwahatiRo, designation);

        PayrollMovementInput movementInput = new PayrollMovementInput(movement, employee, 9, 2026);
        movementInput.setReleasingOffice(kolkataHo);
        movementInput.setReleasingOfficeDays(12);
        movementInput.setTransitJtDays(2);
        movementInput.setTransitLwpDays(0);
        movementInput.setReceivingOffice(guwahatiRo);
        movementInput.setReceivingOfficeDays(16);

        StateMaster westBengal = new StateMaster("WB", "West Bengal", StateType.STATE, true);
        StateMaster assam = new StateMaster("AS", "Assam", StateType.STATE, true);
        assam.setRemoteArea(true);
        assam.setRemoteAllowancePercentage(new BigDecimal("10.00"));

        when(payrollBatchRepository.findById(900L)).thenReturn(Optional.of(batch));
        when(payrollMonthlyRecordRepository.deleteByBatch_Id(900L)).thenReturn(0L);
        when(encashmentRepository.findByPayrollBatch_IdAndPayrollProcessedFalse(900L)).thenReturn(List.of());
        when(encashmentRepository.findEligibleForPayrollBatch(ApprovalStatus.APPROVED, 900L)).thenReturn(List.of());
        when(employeeRepository.findByStatus(EmployeeStatus.ACTIVE)).thenReturn(List.of(employee));

        when(payrollMovementInputRepository.findByEmployee_IdAndPayMonthAndPayYearAndPayrollAppliedFalse(2801L, 9, 2026))
                .thenReturn(List.of(movementInput));
        when(regularPayFixationRepository.findByEmployee_IdOrderByEffectiveFromAsc(2801L))
                .thenReturn(List.of(oldFixation, newFixation));

        when(daRateHistoryRepository.findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                eq(ScaleType.IDA), any(LocalDate.class)))
                .thenReturn(Optional.of(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 7, 1), new BigDecimal("55.70"), true)));

        when(quarterAllotmentService.isHraSuppressed(anyLong(), any(LocalDate.class), any(LocalDate.class))).thenReturn(false);
        when(quarterAllotmentService.findActiveOccupancy(2801L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(Optional.empty());

        when(payrollHraRateRepository.findActiveOn(any(LocalDate.class))).thenReturn(List.of(
                hraRate(CityClass.X, "30.00"), hraRate(CityClass.Y, "20.00")));

        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(40L, "X"))
                .thenReturn(List.of(new TransportAllowanceRate(gradeScale, "X", new BigDecimal("7200.00"), LocalDate.of(2020, 4, 1))));
        when(transportAllowanceRateRepository.findByGradeScale_IdAndCityClassOrderByEffectiveFromDesc(40L, "Y"))
                .thenReturn(List.of(new TransportAllowanceRate(gradeScale, "Y", new BigDecimal("3600.00"), LocalDate.of(2020, 4, 1))));

        when(stateMasterRepository.findByStateNameIgnoreCase("West Bengal")).thenReturn(Optional.of(westBengal));
        when(stateMasterRepository.findByStateNameIgnoreCase("Assam")).thenReturn(Optional.of(assam));
        // Real payroll_ptax_slabs seed - West Bengal (unused by this scenario since the employee's
        // effective month-end station is Guwahati/Assam, but stubbed for completeness) and Assam.
        when(ptaxSlabRepository.findByStateCodeOrderBySlabMinAsc("WB")).thenReturn(List.of(
                new PtaxSlab("WB", new BigDecimal("0.00"), new BigDecimal("10000.00"), new BigDecimal("0.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("WB", new BigDecimal("10001.00"), new BigDecimal("15000.00"), new BigDecimal("110.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("WB", new BigDecimal("15001.00"), new BigDecimal("20000.00"), new BigDecimal("130.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("WB", new BigDecimal("20001.00"), new BigDecimal("40000.00"), new BigDecimal("150.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("WB", new BigDecimal("40001.00"), null, new BigDecimal("200.00"), null, null, LocalDate.of(2020, 4, 1))));
        when(ptaxSlabRepository.findByStateCodeOrderBySlabMinAsc("AS")).thenReturn(List.of(
                new PtaxSlab("AS", new BigDecimal("0.00"), new BigDecimal("10000.00"), new BigDecimal("0.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("AS", new BigDecimal("10001.00"), new BigDecimal("15000.00"), new BigDecimal("150.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("AS", new BigDecimal("15001.00"), new BigDecimal("25000.00"), new BigDecimal("180.00"), null, null, LocalDate.of(2020, 4, 1)),
                new PtaxSlab("AS", new BigDecimal("25001.00"), null, new BigDecimal("208.00"), 3, new BigDecimal("212.00"), LocalDate.of(2020, 4, 1))));

        when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(eq(2801L), any())).thenReturn(List.of());
        when(npsDeclarationRepository.findByEmployee_IdAndFinancialYear(2801L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollStatutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("CPF_EMP_RATE")).thenReturn(Optional.empty());

        when(payrollTdsEngine.computeMonthlyTds(any(), anyString(), any(Integer.class), any(Integer.class), any(), any(), any(), anyBoolean()))
                .thenReturn(new PayrollTdsEngine.TdsResult(BigDecimal.ZERO, BigDecimal.ZERO, false, null, 6, BigDecimal.ZERO, BigDecimal.ZERO));
        when(payrollMonthlyRecordRepository.saveAndFlush(any(PayrollMonthlyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PayrollHraRate hraRate(CityClass cityClass, String percentage) {
        return new PayrollHraRate(cityClass.name(), new BigDecimal(percentage), BigDecimal.ZERO, LocalDate.of(2024, 1, 1), null, null);
    }

    private List<PayrollMonthlyHeadItem> capturedHeadItems() {
        ArgumentCaptor<PayrollMonthlyHeadItem> captor = ArgumentCaptor.forClass(PayrollMonthlyHeadItem.class);
        verify(payrollMonthlyHeadItemRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private BigDecimal amountForHead(int headCount) {
        return capturedHeadItems().stream()
                .filter(item -> item.getHeadCount() == headCount)
                .map(PayrollMonthlyHeadItem::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    @Test
    void septemberGrossSalary_splitsAcrossOldOffice_transit_andNewOfficeAtPromotedBasic() {
        PayrollBatch result = service.processBatch(900L);

        // Earned Basic (Head 1): every individual head amount is rounded to the nearest whole rupee
        // (HALF_UP), not the nearest paisa - see PayrollBatchComputationService.round()'s own javadoc.
        // 12 days @ 61,800 (Kolkata) + 1 day @ 61,800 (JT, pre-promotion) + 1 day @ 70,000 (JT, the
        // promotion's own effective day) + 16 days @ 70,000 (Guwahati).
        // = round(61800*12/30) + round(61800*1/30) + round(70000*1/30) + round(70000*16/30)
        // = 24720 + 2060 + 2333 + 37333 = 66,446
        assertThat(amountForHead(HEAD_BASIC)).isEqualByComparingTo("66446");

        // DA (Head 8, IDA, real 55.70% rate - da_rate_history, effective_from 2026-07-01): rounded ONCE
        // on the month's TOTAL Earned Basic, not per slice - round(66446 * 55.70 / 100) = 37,010.
        assertThat(amountForHead(HEAD_DA_IDA)).isEqualByComparingTo("37010");

        // HRA (Head 9): Kolkata slices (X, 30%) for the pre-transfer + JT days, Guwahati slice (Y, 20%) -
        // each slice's own (already whole-rupee) Earned Basic, rounded per slice:
        // round(24720*30%)=7416 + round(2060*30%)=618 + round(2333*30%)=700 + round(37333*20%)=7467
        // = 16,201
        assertThat(amountForHead(HEAD_HRA)).isEqualByComparingTo("16201");

        // Transport Allowance (Head 10): flat monthly rate per office (X: 7200+round(55.70%)=11,210,
        // Y: 3600+round(55.70%)=5,605), prorated by each slice's own days/30 - EXCEPT the two Joining
        // Time days, which earn none (a commute allowance for a still-in-transit day doesn't apply):
        // round(11210*12/30)=4484 + 0 (2d transit) + round(5605*16/30)=2989 = 7,473
        assertThat(amountForHead(HEAD_TRANSPORT)).isEqualByComparingTo("7473");

        // Remote Area Allowance (Head 21) - only the Guwahati/Assam slice qualifies:
        // round(37333 * 10%) = 3,733. Kolkata/West Bengal is not a remote-area state.
        assertThat(amountForHead(HEAD_REMOTE_AREA)).isEqualByComparingTo("3733");

        // Gross = Basic + DA + HRA + Transport + Remote Area (no Head 20 leave encashment this month).
        // 66446 + 37010 + 16201 + 7473 + 3733 = 130,863
        assertThat(result.getTotalGross()).isEqualByComparingTo("130863");
    }

    @Test
    void septemberDeductions_cpfOnBasicPlusDa() {
        service.processBatch(900L);

        // CPF (Head 27): 12% of (Basic 66,446 + DA 37,010) = round(12% of 103,456) = round(12414.72) = 12,415.
        assertThat(amountForHead(HEAD_CPF)).isEqualByComparingTo("12415");
    }

    @Test
    void septemberPTax_resolvesFromEffectiveMonthEndStation_notTheEmployeeRecordsStaticOffice() {
        // The employee's own regionalOffice field is still Kolkata (the movement lifecycle never
        // updates it - see class javadoc), but the employee ends September at Guwahati/Assam, so P-Tax
        // MUST resolve against the Assam slab (Rs. 208 for gross > Rs. 25,000), not West Bengal's
        // (Rs. 200) - this is the dynamic month-end resolution PayrollBatchComputationService.resolvePtax()
        // now performs, keyed off the LAST EarningsSlice chronologically rather than employee.getRegionalOffice().
        service.processBatch(900L);

        assertThat(amountForHead(HEAD_PTAX)).isEqualByComparingTo("208.00");
        assertThat(amountForHead(HEAD_PTAX)).isNotEqualByComparingTo("200.00");
    }

    @Test
    void septemberRecord_accountsForAllThirtyDaysAcrossTheMovement_noResidualLop() {
        service.processBatch(900L);

        ArgumentCaptor<PayrollMonthlyRecord> captor = ArgumentCaptor.forClass(PayrollMonthlyRecord.class);
        verify(payrollMonthlyRecordRepository).saveAndFlush(captor.capture());
        PayrollMonthlyRecord record = captor.getValue();

        // 12 (Kolkata) + 2 (Joining Time, paid) + 16 (Guwahati) = 30 - every day of September is
        // accounted for and paid, so there is no loss-of-pay for this employee this month.
        assertThat(record.getDaysLop()).isEqualByComparingTo("0.0");
        assertThat(record.getDaysPresent()).isEqualByComparingTo("30.0");

        // Descriptive columns (including loc_code, the same "effective month-end station" P-Tax uses)
        // reflect where the employee ends the month: Guwahati (Y-class, IDA).
        assertThat(record.getLocCode()).isEqualTo("GUW-RO");
        assertThat(record.getCityClass()).isEqualTo("Y");
        assertThat(record.getPayPattern()).isEqualTo("IDA");
    }

    @Test
    void septemberGrossSalary_isNotComputedAsIfTheWholeMonthWereAtTheNewPromotedOfficeAndBasic() {
        // Regression guard for the documented pre-existing gap: naively taking whichever
        // RegularPayFixation is "current" for the whole month (70,000 @ Guwahati/Y for all 30 days,
        // ignoring the Kolkata/old-basic portion) would produce a materially different, wrong gross.
        // 70000 (basic) + round(55.70% DA)=38990 + 20% HRA on basic (14000) + flat Guwahati TA
        // (3600 + round(55.70%)=5605) + 10% remote area on basic (7000) = 135,595 - not what this
        // employee actually earned.
        PayrollBatch result = service.processBatch(900L);

        assertThat(result.getTotalGross()).isNotEqualByComparingTo("135595");
        assertThat(result.getTotalGross()).isEqualByComparingTo("130863");
    }
}
