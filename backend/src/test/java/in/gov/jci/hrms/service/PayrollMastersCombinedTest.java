package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EmployeeAddressRequest;
import in.gov.jci.hrms.dto.NpsAdminSummaryResponse;
import in.gov.jci.hrms.dto.NpsDeclarationRequest;
import in.gov.jci.hrms.dto.NpsPreviewResponse;
import in.gov.jci.hrms.dto.QuarterAllotmentRequest;
import in.gov.jci.hrms.dto.QuarterAllotmentResponse;
import in.gov.jci.hrms.dto.SalaryHeadResponse;
import in.gov.jci.hrms.dto.SalaryHeadUpdateRequest;
import in.gov.jci.hrms.dto.StatutoryHeadResponse;
import in.gov.jci.hrms.dto.StatutoryHeadUpdateRequest;
import in.gov.jci.hrms.dto.StatutoryParameterResponse;
import in.gov.jci.hrms.dto.StatutoryParameterReviseRequest;
import in.gov.jci.hrms.dto.VehicleAllotmentRequest;
import in.gov.jci.hrms.dto.VehicleAllotmentResponse;
import in.gov.jci.hrms.dto.VehicleAllotmentSurrenderRequest;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeNpsDeclaration;
import in.gov.jci.hrms.entity.EmployeeQuarterAllotment;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.EmployeeVehicleAllotment;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollStatutoryParameter;
import in.gov.jci.hrms.entity.QuarterAllotmentStatus;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.SalaryHead;
import in.gov.jci.hrms.entity.SalaryHeadEffectType;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.entity.StatutoryHead;
import in.gov.jci.hrms.entity.StatutoryParamValueType;
import in.gov.jci.hrms.entity.VehicleAllotmentStatus;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeNpsDeclarationRepository;
import in.gov.jci.hrms.repository.EmployeeQuarterAllotmentRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeVehicleAllotmentRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.ProcurementAllowanceRateRepository;
import in.gov.jci.hrms.repository.PtaxSlabRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.repository.SalaryHeadRepository;
import in.gov.jci.hrms.repository.StateMasterRepository;
import in.gov.jci.hrms.repository.StatutoryHeadRepository;
import in.gov.jci.hrms.repository.TransportAllowanceRateRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Combined coverage for the Payroll Masters II feature set: editable Salary/Statutory Heads, the NPS
 * Declaration Desk's tightened contribution range and live-preview/admin-summary endpoints, Statutory
 * Parameter revision (no gaps/overlaps), Vehicle Allotment's has_office_car sync, and address-based
 * Company Accommodation (address storage, HRA suppression, employee-address sync).
 */
@ExtendWith(MockitoExtension.class)
class PayrollMastersCombinedTest {

    private static Employee employee(Long id) {
        Employee employee = new Employee("EMP" + id, "Test", "Employee" + id, "employee" + id + "@example.com",
                LocalDate.of(2020, 1, 1), null, null);
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    /** SalaryHead/StatutoryHead only expose a protected no-arg constructor (no public builder) - reflection instantiates a bare row for tests to mutate via the entity's own setters, same as production code would after JpaRepository.findById() hydrates one. */
    private static <T> T newViaProtectedConstructor(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    @Nested
    class SalaryAndStatutoryHeadUpdateTests {

        @Mock private TransportAllowanceRateRepository transportAllowanceRateRepository;
        @Mock private ProcurementAllowanceRateRepository procurementAllowanceRateRepository;
        @Mock private PtaxSlabRepository ptaxSlabRepository;
        @Mock private SalaryHeadRepository salaryHeadRepository;
        @Mock private StatutoryHeadRepository statutoryHeadRepository;
        @Mock private GradeScaleMasterRepository gradeScaleMasterRepository;
        @Mock private DesignationRepository designationRepository;
        @Mock private StateMasterRepository stateMasterRepository;

        private PayrollMasterService payrollMasterService;

        @BeforeEach
        void setUp() {
            payrollMasterService = new PayrollMasterServiceImpl(transportAllowanceRateRepository, procurementAllowanceRateRepository,
                    ptaxSlabRepository, salaryHeadRepository, statutoryHeadRepository, gradeScaleMasterRepository,
                    designationRepository, stateMasterRepository);
        }

        @Test
        void updateSalaryHead_mutatesEveryEditableField() throws ReflectiveOperationException {
            SalaryHead head = newViaProtectedConstructor(SalaryHead.class);
            ReflectionTestUtils.setField(head, "headCount", 1);
            ReflectionTestUtils.setField(head, "description", "Basic");
            ReflectionTestUtils.setField(head, "shortName", "BASIC");
            ReflectionTestUtils.setField(head, "effectType", SalaryHeadEffectType.EARNING);
            when(salaryHeadRepository.findById(1)).thenReturn(Optional.of(head));

            SalaryHeadUpdateRequest request = new SalaryHeadUpdateRequest("Basic Pay (Revised)", "BASIC_PAY",
                    SalaryHeadEffectType.EARNING, true, "BOTH", 5, true, "GL-1001");

            SalaryHeadResponse response = payrollMasterService.updateSalaryHead(1, request);

            assertThat(response.description()).isEqualTo("Basic Pay (Revised)");
            assertThat(response.shortName()).isEqualTo("BASIC_PAY");
            assertThat(response.isVariable()).isTrue();
            assertThat(response.applicableFor()).isEqualTo("BOTH");
            assertThat(response.salSlipVis()).isEqualTo(5);
            assertThat(response.basicDependent()).isTrue();
            assertThat(response.refAccountCode()).isEqualTo("GL-1001");
        }

        @Test
        void updateSalaryHead_whenMissing_throwsMasterDataNotFoundException() {
            when(salaryHeadRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> payrollMasterService.updateSalaryHead(999,
                    new SalaryHeadUpdateRequest("x", "x", SalaryHeadEffectType.EARNING, false, "REGULAR", 1, false, "GL")))
                    .isInstanceOf(MasterDataNotFoundException.class);
        }

        @Test
        void updateStatutoryHead_mutatesDescriptionAndShortName() throws ReflectiveOperationException {
            StatutoryHead head = newViaProtectedConstructor(StatutoryHead.class);
            ReflectionTestUtils.setField(head, "statHeadCount", 1);
            ReflectionTestUtils.setField(head, "statHeadDescr", "Contributory Provident Fund");
            ReflectionTestUtils.setField(head, "statHeadShortName", "CPF");
            when(statutoryHeadRepository.findById(1)).thenReturn(Optional.of(head));

            StatutoryHeadUpdateRequest request = new StatutoryHeadUpdateRequest("Contributory Provident Fund (CPF)", "CPF-NEW");
            StatutoryHeadResponse response = payrollMasterService.updateStatutoryHead(1, request);

            assertThat(response.statHeadDescr()).isEqualTo("Contributory Provident Fund (CPF)");
            assertThat(response.statHeadShortName()).isEqualTo("CPF-NEW");
        }

        @Test
        void updateStatutoryHead_whenMissing_throwsMasterDataNotFoundException() {
            when(statutoryHeadRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> payrollMasterService.updateStatutoryHead(999, new StatutoryHeadUpdateRequest("x", "x")))
                    .isInstanceOf(MasterDataNotFoundException.class);
        }
    }

    @Nested
    class NpsPercentageRangeValidationTests {

        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        private Set<ConstraintViolation<NpsDeclarationRequest>> violationsFor(String percentage) {
            NpsDeclarationRequest request = new NpsDeclarationRequest(1L, new BigDecimal(percentage), null);
            return validator.validate(request);
        }

        @Test
        void rejects_belowMinimum_2_50() {
            assertThat(violationsFor("2.50")).isNotEmpty();
        }

        @Test
        void rejects_aboveMaximum_11_00() {
            assertThat(violationsFor("11.00")).isNotEmpty();
        }

        @Test
        void accepts_minimum_3_00() {
            assertThat(violationsFor("3.00")).isEmpty();
        }

        @Test
        void accepts_midRange_7_50() {
            assertThat(violationsFor("7.50")).isEmpty();
        }

        @Test
        void accepts_maximum_10_00() {
            assertThat(violationsFor("10.00")).isEmpty();
        }
    }

    @Nested
    class NpsPreviewAndAdminSummaryTests {

        @Mock private EmployeeNpsDeclarationRepository npsDeclarationRepository;
        @Mock private EmployeeRepository employeeRepository;
        @Mock private RegularPayFixationRepository regularPayFixationRepository;
        @Mock private PayrollComputationService payrollComputationService;

        private NpsDeclarationService npsDeclarationService;

        @BeforeEach
        void setUp() {
            npsDeclarationService = new NpsDeclarationServiceImpl(npsDeclarationRepository, employeeRepository,
                    regularPayFixationRepository, payrollComputationService);
        }

        private RegularPayFixation fixationWithBasic(Employee employee, BigDecimal basicPay) {
            GradeScaleMaster gradeScale = new GradeScaleMaster("E5", Cadre.EXECUTIVE, 6, false,
                    new BigDecimal("60000"), new BigDecimal("120000"));
            return new RegularPayFixation(employee, gradeScale, basicPay, LocalDate.of(2024, 4, 1));
        }

        @Test
        void preview_returnsCurrentBasicAndComputedDa() {
            Employee employee = employee(100L);
            RegularPayFixation fixation = fixationWithBasic(employee, new BigDecimal("40000.00"));
            when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
            when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(100L)).thenReturn(Optional.of(fixation));
            when(payrollComputationService.resolveDaPercentage(eq(ScaleType.IDA), any())).thenReturn(new BigDecimal("53.00"));
            when(payrollComputationService.computeDearnessAllowance(new BigDecimal("40000.00"), new BigDecimal("53.00")))
                    .thenReturn(new BigDecimal("21200.00"));
            when(npsDeclarationRepository.findByEmployee_IdOrderByFinancialYearDesc(100L)).thenReturn(List.of());

            NpsPreviewResponse preview = npsDeclarationService.preview(100L);

            assertThat(preview.basicPay()).isEqualByComparingTo("40000.00");
            assertThat(preview.dearnessAllowance()).isEqualByComparingTo("21200.00");
            assertThat(preview.alreadyDeclaredForCurrentFy()).isFalse();

            // The live "Estimated Monthly Deduction" badge itself is computed reactively on the frontend from these
            // two values as the user types a percentage - verifying the formula here against the same preview data:
            // round((Basic + DA) * pct / 100).
            BigDecimal basicPlusDa = preview.basicPay().add(preview.dearnessAllowance());
            BigDecimal estimatedDeduction = basicPlusDa.multiply(new BigDecimal("7.50"))
                    .divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
            assertThat(estimatedDeduction).isEqualByComparingTo("4590");
        }

        @Test
        void adminSummary_pendingListExcludesEmployeesWhoAlreadyDeclared() {
            Employee declared = employee(1L);
            Employee pending1 = employee(2L);
            Employee pending2 = employee(3L);

            EmployeeNpsDeclaration declaration = new EmployeeNpsDeclaration(declared, "2026-2027",
                    new BigDecimal("5.00"), LocalDate.of(2026, 6, 1), null);
            when(npsDeclarationRepository.findByFinancialYear("2026-2027")).thenReturn(List.of(declaration));
            when(employeeRepository.findByNpsEligibleTrueAndStatus(EmployeeStatus.ACTIVE))
                    .thenReturn(List.of(declared, pending1, pending2));
            when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(1L))
                    .thenReturn(Optional.of(fixationWithBasic(declared, new BigDecimal("30000.00"))));
            when(payrollComputationService.resolveDaPercentage(any(), any())).thenReturn(new BigDecimal("50.00"));
            when(payrollComputationService.computeDearnessAllowance(any(), any())).thenReturn(new BigDecimal("15000.00"));

            NpsAdminSummaryResponse summary = npsDeclarationService.adminSummary("2026-2027");

            assertThat(summary.submitted()).extracting("employeeId").containsExactly(1L);
            assertThat(summary.pending()).extracting("employeeId").containsExactlyInAnyOrder(2L, 3L);
        }
    }

    @Nested
    class StatutoryParameterRevisionTests {

        @Mock private PayrollStatutoryParameterRepository statutoryParameterRepository;

        private PayrollStatutoryParameterService statutoryParameterService;

        @BeforeEach
        void setUp() {
            statutoryParameterService = new PayrollStatutoryParameterServiceImpl(statutoryParameterRepository);
        }

        @Test
        void revise_closesOutPriorRowImmediatelyBeforeNewEffectiveFrom_noGapOrOverlap() {
            PayrollStatutoryParameter current = new PayrollStatutoryParameter("CPF_EMP_RATE", "Employee CPF Contribution Rate",
                    new BigDecimal("12.0000"), StatutoryParamValueType.PERCENTAGE, LocalDate.of(2020, 4, 1), null);
            when(statutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("CPF_EMP_RATE")).thenReturn(Optional.of(current));
            when(statutoryParameterRepository.saveAndFlush(any(PayrollStatutoryParameter.class))).thenAnswer(inv -> inv.getArgument(0));

            StatutoryParameterReviseRequest request = new StatutoryParameterReviseRequest(new BigDecimal("13.0000"),
                    LocalDate.of(2027, 4, 1), "Revised per new circular");
            StatutoryParameterResponse revised = statutoryParameterService.revise("CPF_EMP_RATE", request);

            // No gap: the old row's new effectiveTo is exactly one day before the new row's effectiveFrom.
            assertThat(current.getEffectiveTo()).isEqualTo(LocalDate.of(2027, 3, 31));
            assertThat(current.getEffectiveTo().plusDays(1)).isEqualTo(revised.effectiveFrom());
            // No overlap: the new row is open-ended (still active) and starts strictly after the old row's own start.
            assertThat(revised.effectiveTo()).isNull();
            assertThat(revised.effectiveFrom()).isAfter(LocalDate.of(2020, 4, 1));
            assertThat(revised.paramValue()).isEqualByComparingTo("13.0000");
        }

        @Test
        void revise_withNewEffectiveFromNotAfterCurrent_throwsBusinessRuleViolationException() {
            PayrollStatutoryParameter current = new PayrollStatutoryParameter("LWF_WB_AMOUNT", "West Bengal LWF", new BigDecimal("3.0000"),
                    StatutoryParamValueType.AMOUNT, LocalDate.of(2020, 4, 1), null);
            when(statutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("LWF_WB_AMOUNT")).thenReturn(Optional.of(current));

            StatutoryParameterReviseRequest request = new StatutoryParameterReviseRequest(new BigDecimal("5.0000"),
                    LocalDate.of(2020, 4, 1), null);

            assertThatThrownBy(() -> statutoryParameterService.revise("LWF_WB_AMOUNT", request))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void revise_whenParamKeyUnknown_throwsMasterDataNotFoundException() {
            when(statutoryParameterRepository.findByParamKeyAndEffectiveToIsNull("UNKNOWN")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> statutoryParameterService.revise("UNKNOWN",
                    new StatutoryParameterReviseRequest(BigDecimal.ONE, LocalDate.now(), null)))
                    .isInstanceOf(MasterDataNotFoundException.class);
        }
    }

    @Nested
    class VehicleAllotmentOfficeCarSyncTests {

        @Mock private EmployeeVehicleAllotmentRepository vehicleAllotmentRepository;
        @Mock private EmployeeRepository employeeRepository;

        private EmployeeVehicleAllotmentService vehicleAllotmentService;

        @BeforeEach
        void setUp() {
            vehicleAllotmentService = new EmployeeVehicleAllotmentService(vehicleAllotmentRepository, employeeRepository);
        }

        private VehicleAllotmentRequest request() {
            return new VehicleAllotmentRequest("ORD/2026/001", "WB-06X-1234", "Maruti Ertiga", true, true, true,
                    new BigDecimal("2000.00"), LocalDate.of(2026, 1, 1), null);
        }

        @Test
        void create_setsHasOfficeCarTrueOnEmployee() {
            Employee employee = employee(50L);
            when(employeeRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(50L, VehicleAllotmentStatus.ACTIVE)).thenReturn(List.of());
            when(vehicleAllotmentRepository.saveAndFlush(any(EmployeeVehicleAllotment.class))).thenAnswer(inv -> {
                EmployeeVehicleAllotment a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 1L);
                return a;
            });

            assertThat(employee.isOfficeCarProvided()).isFalse();
            vehicleAllotmentService.create(50L, request());
            assertThat(employee.isOfficeCarProvided()).isTrue();
        }

        @Test
        void create_whenAlreadyHasActiveVehicle_throwsBusinessRuleViolationException() {
            Employee employee = employee(50L);
            when(employeeRepository.findById(50L)).thenReturn(Optional.of(employee));
            EmployeeVehicleAllotment existing = new EmployeeVehicleAllotment(employee, null, "WB-01A-0001", null,
                    true, true, true, new BigDecimal("2000.00"), LocalDate.of(2025, 1, 1), null);
            when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(50L, VehicleAllotmentStatus.ACTIVE)).thenReturn(List.of(existing));

            assertThatThrownBy(() -> vehicleAllotmentService.create(50L, request())).isInstanceOf(BusinessRuleViolationException.class);
        }

        @Test
        void surrender_whenNoOtherActiveVehicle_setsHasOfficeCarFalseOnEmployee() {
            Employee employee = employee(50L);
            employee.setOfficeCarProvided(true);
            EmployeeVehicleAllotment allotment = new EmployeeVehicleAllotment(employee, null, "WB-06X-1234", "Maruti Ertiga",
                    true, true, true, new BigDecimal("2000.00"), LocalDate.of(2026, 1, 1), null);
            ReflectionTestUtils.setField(allotment, "id", 7L);
            when(vehicleAllotmentRepository.findById(7L)).thenReturn(Optional.of(allotment));
            when(vehicleAllotmentRepository.saveAndFlush(any(EmployeeVehicleAllotment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(50L, VehicleAllotmentStatus.ACTIVE)).thenReturn(List.of());

            VehicleAllotmentResponse response = vehicleAllotmentService.surrender(50L, 7L,
                    new VehicleAllotmentSurrenderRequest(LocalDate.of(2026, 8, 1), "Transferred"));

            assertThat(response.status()).isEqualTo(VehicleAllotmentStatus.SURRENDERED);
            assertThat(employee.isOfficeCarProvided()).isFalse();
        }

        @Test
        void surrender_whenAnotherActiveVehicleStillExists_leavesHasOfficeCarTrue() {
            Employee employee = employee(50L);
            employee.setOfficeCarProvided(true);
            EmployeeVehicleAllotment allotment = new EmployeeVehicleAllotment(employee, null, "WB-06X-1234", "Maruti Ertiga",
                    true, true, true, new BigDecimal("2000.00"), LocalDate.of(2026, 1, 1), null);
            ReflectionTestUtils.setField(allotment, "id", 7L);
            EmployeeVehicleAllotment stillActive = new EmployeeVehicleAllotment(employee, null, "WB-07Y-9999", "Toyota Innova",
                    true, true, true, new BigDecimal("2000.00"), LocalDate.of(2026, 2, 1), null);
            when(vehicleAllotmentRepository.findById(7L)).thenReturn(Optional.of(allotment));
            when(vehicleAllotmentRepository.saveAndFlush(any(EmployeeVehicleAllotment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(vehicleAllotmentRepository.findByEmployee_IdAndStatus(50L, VehicleAllotmentStatus.ACTIVE)).thenReturn(List.of(stillActive));

            vehicleAllotmentService.surrender(50L, 7L, new VehicleAllotmentSurrenderRequest(LocalDate.of(2026, 8, 1), null));

            assertThat(employee.isOfficeCarProvided()).isTrue();
        }
    }

    @Nested
    class CompanyAccommodationTests {

        @Mock private EmployeeQuarterAllotmentRepository quarterAllotmentRepository;
        @Mock private EmployeeRepository employeeRepository;
        @Mock private EmployeeAddressService employeeAddressService;
        @Mock private StateMasterRepository stateMasterRepository;

        private EmployeeQuarterAllotmentService quarterAllotmentService;

        @BeforeEach
        void setUp() {
            quarterAllotmentService = new EmployeeQuarterAllotmentService(quarterAllotmentRepository, employeeRepository,
                    employeeAddressService, stateMasterRepository);
        }

        private QuarterAllotmentRequest request(boolean sync) {
            return new QuarterAllotmentRequest("ORD/ACC/001", "123 Lake Town", "Block A", "Kolkata", "WB", "700089",
                    sync, new BigDecimal("1200.00"), new BigDecimal("150.00"), new BigDecimal("300.00"),
                    LocalDate.of(2026, 1, 1), null, QuarterAllotmentStatus.OCCUPIED, null);
        }

        @Test
        void create_storesAddressDetails() {
            Employee employee = employee(60L);
            when(employeeRepository.findById(60L)).thenReturn(Optional.of(employee));
            when(quarterAllotmentRepository.findByEmployee_IdAndStatus(60L, QuarterAllotmentStatus.OCCUPIED)).thenReturn(List.of());
            when(quarterAllotmentRepository.saveAndFlush(any(EmployeeQuarterAllotment.class))).thenAnswer(inv -> {
                EmployeeQuarterAllotment a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 1L);
                return a;
            });

            QuarterAllotmentResponse response = quarterAllotmentService.create(60L, request(false));

            assertThat(response.addressLine1()).isEqualTo("123 Lake Town");
            assertThat(response.city()).isEqualTo("Kolkata");
            assertThat(response.stateCode()).isEqualTo("WB");
            assertThat(response.pincode()).isEqualTo("700089");
            assertThat(response.status()).isEqualTo(QuarterAllotmentStatus.OCCUPIED);
        }

        @Test
        void create_withSyncCurrentAddressTrue_upsertsEmployeePresentAddress() {
            Employee employee = employee(60L);
            when(employeeRepository.findById(60L)).thenReturn(Optional.of(employee));
            when(quarterAllotmentRepository.findByEmployee_IdAndStatus(60L, QuarterAllotmentStatus.OCCUPIED)).thenReturn(List.of());
            when(quarterAllotmentRepository.saveAndFlush(any(EmployeeQuarterAllotment.class))).thenAnswer(inv -> {
                EmployeeQuarterAllotment a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 1L);
                return a;
            });
            when(stateMasterRepository.findByStateCode("WB")).thenReturn(Optional.empty());

            quarterAllotmentService.create(60L, request(true));

            verify(employeeAddressService).upsert(eq(60L), any(EmployeeAddressRequest.class));
        }

        @Test
        void create_withSyncCurrentAddressFalse_doesNotTouchEmployeeAddress() {
            Employee employee = employee(60L);
            when(employeeRepository.findById(60L)).thenReturn(Optional.of(employee));
            when(quarterAllotmentRepository.findByEmployee_IdAndStatus(60L, QuarterAllotmentStatus.OCCUPIED)).thenReturn(List.of());
            when(quarterAllotmentRepository.saveAndFlush(any(EmployeeQuarterAllotment.class))).thenAnswer(inv -> {
                EmployeeQuarterAllotment a = inv.getArgument(0);
                ReflectionTestUtils.setField(a, "id", 1L);
                return a;
            });

            quarterAllotmentService.create(60L, request(false));

            verify(employeeAddressService, never()).upsert(any(), any());
        }

        @Test
        void isHraSuppressed_trueWhileAnOccupiedAllotmentCoversTheDate() {
            Employee employee = employee(60L);
            EmployeeQuarterAllotment occupied = new EmployeeQuarterAllotment(employee, null, "123 Lake Town", null,
                    "Kolkata", "WB", "700089", true, new BigDecimal("1200.00"), new BigDecimal("150.00"),
                    new BigDecimal("300.00"), LocalDate.of(2026, 1, 1), null);
            when(quarterAllotmentRepository.findByEmployee_IdAndStatus(60L, QuarterAllotmentStatus.OCCUPIED))
                    .thenReturn(List.of(occupied));

            assertThat(quarterAllotmentService.isHraSuppressed(60L, LocalDate.of(2026, 6, 1))).isTrue();
            assertThat(quarterAllotmentService.isHraSuppressed(60L, LocalDate.of(2025, 12, 31))).isFalse();
        }

        @Test
        void isHraSuppressed_falseAfterVacatedOn() {
            Employee employee = employee(60L);
            EmployeeQuarterAllotment vacated = new EmployeeQuarterAllotment(employee, null, "123 Lake Town", null,
                    "Kolkata", "WB", "700089", true, new BigDecimal("1200.00"), new BigDecimal("150.00"),
                    new BigDecimal("300.00"), LocalDate.of(2026, 1, 1), null);
            vacated.setVacatedOn(LocalDate.of(2026, 3, 31));
            vacated.setStatus(QuarterAllotmentStatus.VACATED);
            when(quarterAllotmentRepository.findByEmployee_IdAndStatus(60L, QuarterAllotmentStatus.OCCUPIED)).thenReturn(List.of());

            assertThat(quarterAllotmentService.isHraSuppressed(60L, LocalDate.of(2026, 6, 1))).isFalse();
        }

        @Test
        void create_withVacatedOnBeforeAllottedFrom_throwsBusinessRuleViolationException() {
            QuarterAllotmentRequest invalid = new QuarterAllotmentRequest(null, "123 Lake Town", null, "Kolkata", "WB",
                    "700089", false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), QuarterAllotmentStatus.VACATED, null);

            assertThatThrownBy(() -> quarterAllotmentService.create(60L, invalid))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }
}
