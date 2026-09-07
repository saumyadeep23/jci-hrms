package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.MovementOrderCreateRequest;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.MovementOrderType;
import in.gov.jci.hrms.entity.RegionalOffice;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.entity.TransferNature;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DepartmentalPurchaseCentreRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeMovementRecordRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.MovementOrderRepository;
import in.gov.jci.hrms.repository.RegionalOfficeRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.service.pdf.PromotionOrderPdfGenerator;
import in.gov.jci.hrms.service.pdf.TransferOrderPdfGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovementOrderServiceTest {

    @Mock private MovementOrderRepository movementOrderRepository;
    @Mock private EmployeeMovementRecordRepository movementRecordRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RegionalOfficeRepository regionalOfficeRepository;
    @Mock private DepartmentalPurchaseCentreRepository departmentalPurchaseCentreRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private JoiningTimeCalculatorService joiningTimeCalculatorService;
    @Mock private GeofenceService geofenceService;
    @Mock private TransferOrderPdfGenerator transferOrderPdfGenerator;
    @Mock private PromotionOrderPdfGenerator promotionOrderPdfGenerator;
    @Mock private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Mock private RegularPayFixationRepository regularPayFixationRepository;

    private MovementOrderService service;

    private Employee employee;
    private Designation designation;
    private RegionalOffice office;

    private void setUp() {
        service = new MovementOrderService(movementOrderRepository, movementRecordRepository, employeeRepository,
                regionalOfficeRepository, departmentalPurchaseCentreRepository, departmentRepository, designationRepository,
                joiningTimeCalculatorService, geofenceService, transferOrderPdfGenerator, promotionOrderPdfGenerator,
                employmentCategoryRepository, gradeScaleMasterRepository, regularPayFixationRepository);

        Department department = new Department("ENG", "Engineering");
        ReflectionTestUtils.setField(department, "id", 1L);
        designation = new Designation("Engineer");
        ReflectionTestUtils.setField(designation, "id", 2L);
        employee = new Employee("EMP0001", Salutation.MR, "Ravi", "Kumar", Gender.MALE, LocalDate.of(1990, 1, 1),
                MaritalStatus.SINGLE, "ABCDE1234F", "CPF00001", "ravi@example.com", "9876543210", LocalDate.of(2015, 1, 1),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);
        office = new RegionalOffice("RO1", "Regional Office 1", "Karnataka", in.gov.jci.hrms.entity.CityClass.Y, true);
        ReflectionTestUtils.setField(office, "id", 3L);

        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        when(regionalOfficeRepository.findById(3L)).thenReturn(Optional.of(office));
        when(designationRepository.findById(2L)).thenReturn(Optional.of(designation));
        when(movementRecordRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private MovementOrderCreateRequest promotionRequest(String toScaleCode, LocalDate effectiveDate) {
        return new MovementOrderCreateRequest(
                MovementOrderType.PROMOTION, "ORD-001", LocalDate.of(2026, 1, 1), effectiveDate, "HR_ADMIN", null,
                100L, TransferNature.ADMINISTRATIVE, false, null, null,
                3L, null, null, 2L, "E2",
                3L, null, null, 2L, "E1",
                null, new BigDecimal("99999.00"), 6, toScaleCode);
    }

    private GradeScaleMaster targetScale() {
        return new GradeScaleMaster("E1", Cadre.EXECUTIVE, 9, false, new BigDecimal("40000.00"), new BigDecimal("140000.00"));
    }

    private EmployeeEmploymentCategory regularCategory() {
        EmployeeEmploymentCategory category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        category.setRegularBasicPay(new BigDecimal("50000.00"));
        return category;
    }

    @Test
    void create_promotionWithScaleCode_fixesNewBasicPayAndDeactivatesPrevious() {
        setUp();
        when(movementOrderRepository.existsByOrderRefNo("ORD-001")).thenReturn(false);
        when(employmentCategoryRepository.findByEmployeeId(100L)).thenReturn(Optional.of(regularCategory()));
        when(gradeScaleMasterRepository.findByScaleCode("E1")).thenReturn(Optional.of(targetScale()));

        RegularPayFixation previous = new RegularPayFixation(employee, targetScale(), new BigDecimal("50000.00"), LocalDate.of(2024, 1, 1));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(100L)).thenReturn(Optional.of(previous));

        service.create(promotionRequest("E1", LocalDate.of(2026, 3, 1)));

        // Closed via a bulk UPDATE now, not by mutating and re-saving the loaded entity - see
        // RegularPayFixationRepository.closeCurrentFixation()'s own javadoc for why (avoids the real
        // Hibernate flush-ordering and lazy-association bugs that entity mutation hit here). previous
        // itself is therefore never mutated by create() - only read from, for currentBasic/incrementCycle.
        verify(regularPayFixationRepository).closeCurrentFixation(100L, LocalDate.of(2026, 2, 28));

        ArgumentCaptor<RegularPayFixation> captor = ArgumentCaptor.forClass(RegularPayFixation.class);
        verify(regularPayFixationRepository).save(captor.capture());
        RegularPayFixation saved = captor.getValue();
        // 50000 * 3% = 1500 (already a multiple of 10); 50000 + 1500 = 51500, within [40000, 140000].
        assertThat(saved.getBasicPay()).isEqualByComparingTo("51500.00");
        assertThat(saved.getFixationReason().name()).isEqualTo("PROMOTION");
    }

    @Test
    void create_promotionForNonRegularEmployee_skipsPayFixation() {
        setUp();
        when(movementOrderRepository.existsByOrderRefNo("ORD-001")).thenReturn(false);
        when(employmentCategoryRepository.findByEmployeeId(100L)).thenReturn(Optional.empty());

        service.create(promotionRequest("E1", LocalDate.of(2026, 3, 1)));

        verify(regularPayFixationRepository, never()).save(any());
    }

    @Test
    void create_promotionWithScaleCodeButNoEffectiveDate_throws() {
        setUp();
        when(movementOrderRepository.existsByOrderRefNo("ORD-001")).thenReturn(false);
        when(employmentCategoryRepository.findByEmployeeId(100L)).thenReturn(Optional.of(regularCategory()));

        assertThatThrownBy(() -> service.create(promotionRequest("E1", null)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
