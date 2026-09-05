package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.IncrementBatchProcessRequest;
import in.gov.jci.hrms.dto.IncrementBatchProcessResponse;
import in.gov.jci.hrms.dto.IncrementDueEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeEmploymentCategory;
import in.gov.jci.hrms.entity.EmploymentCategory;
import in.gov.jci.hrms.entity.FixationReason;
import in.gov.jci.hrms.entity.Gender;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.MaritalStatus;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.Salutation;
import in.gov.jci.hrms.repository.EmployeeEmploymentCategoryRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.EmployeeServiceBookRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dueList() is raw JdbcTemplate SQL with a RowMapper lambda, not worth
 * mocking a ResultSet for - see Manpower4TierReportServiceIntegrationTest
 * for that side's real-DB coverage. This test instead spies the service to
 * stub dueList() with a fixed IncrementDueEntry, so processBatch()'s own
 * logic (fixation supersede, ANNUAL_INCREMENT reason, withheld/ineligible
 * skip) can be verified against mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class IncrementProcessingServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private EmployeeEmploymentCategoryRepository employmentCategoryRepository;
    @Mock
    private EmployeeServiceBookRepository serviceBookRepository;
    @Mock
    private RegularPayFixationRepository regularPayFixationRepository;

    @Spy
    private IncrementProcessingService service = new IncrementProcessingService(
            null, null, null, null, null);

    private Employee employee;
    private EmployeeEmploymentCategory category;
    private GradeScaleMaster scale;
    private RegularPayFixation currentFixation;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "employeeRepository", employeeRepository);
        ReflectionTestUtils.setField(service, "employmentCategoryRepository", employmentCategoryRepository);
        ReflectionTestUtils.setField(service, "serviceBookRepository", serviceBookRepository);
        ReflectionTestUtils.setField(service, "regularPayFixationRepository", regularPayFixationRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Officer");
        employee = new Employee("2801", Salutation.MR, "Test", "Employee", Gender.MALE, LocalDate.of(1990, 3, 1),
                MaritalStatus.SINGLE, "ABCDE1234F", "CPF00001", "test@example.com", "9876543210", LocalDate.of(2010, 3, 15),
                department, designation);
        ReflectionTestUtils.setField(employee, "id", 100L);

        scale = new GradeScaleMaster("E3", in.gov.jci.hrms.entity.Cadre.EXECUTIVE, 3, false,
                new BigDecimal("30000.00"), new BigDecimal("100000.00"));

        category = new EmployeeEmploymentCategory(employee, EmploymentCategory.REGULAR);
        category.setRegularBasicPay(new BigDecimal("61800.00"));

        currentFixation = new RegularPayFixation(employee, scale, new BigDecimal("61800.00"), LocalDate.of(2025, 7, 1));
    }

    /**
     * DPE rules round an increment UP to the next higher multiple of Rs. 10,
     * never to the nearest 10 - Rs. 22,820 (employee 2913, Shashank Pratap)
     * x 3% = Rs. 684.60, which HALF_UP rounding would silently understate to
     * Rs. 680 (68.46 rounds down to 68) instead of the correct Rs. 690.
     */
    @Test
    void roundToNearestTen_roundsUpNotToNearest() {
        BigDecimal basicPay = new BigDecimal("22820.00");
        BigDecimal rawIncrement = basicPay.multiply(new BigDecimal("0.03"));

        BigDecimal increment = IncrementProcessingService.roundToNearestTen(rawIncrement);
        BigDecimal newBasic = basicPay.add(increment);

        assertThat(increment).isEqualByComparingTo("690.00");
        assertThat(newBasic).isEqualByComparingTo("23510.00");
    }

    @Test
    void processBatch_supersedesCurrentFixationAndInsertsAnnualIncrementRow() {
        IncrementDueEntry due = new IncrementDueEntry(
                100L, "2801", "Test Employee", "E3", new BigDecimal("61800.00"), new BigDecimal("1850"),
                new BigDecimal("63650.00"), false, false, null);
        doReturn(List.of(due)).when(service).dueList(any());
        when(employeeRepository.findById(100L)).thenReturn(Optional.of(employee));
        when(employmentCategoryRepository.findByEmployeeId(100L)).thenReturn(Optional.of(category));
        when(regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(100L)).thenReturn(Optional.of(currentFixation));

        IncrementBatchProcessRequest request = new IncrementBatchProcessRequest(
                List.of(100L), "HR/INC/2026/001", LocalDate.of(2026, 4, 1), "Annual increment");
        IncrementBatchProcessResponse response = service.processBatch(request);

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.skippedCount()).isZero();
        assertThat(category.getRegularBasicPay()).isEqualByComparingTo("63650.00");

        assertThat(currentFixation.isCurrent()).isFalse();
        assertThat(currentFixation.getEffectiveTo()).isEqualTo(LocalDate.of(2026, 3, 31));

        ArgumentCaptor<RegularPayFixation> captor = ArgumentCaptor.forClass(RegularPayFixation.class);
        verify(regularPayFixationRepository).save(captor.capture());
        RegularPayFixation newFixation = captor.getValue();
        assertThat(newFixation.getBasicPay()).isEqualByComparingTo("63650.00");
        assertThat(newFixation.getGradeScale()).isEqualTo(scale);
        assertThat(newFixation.getFixationReason()).isEqualTo(FixationReason.ANNUAL_INCREMENT);
        assertThat(newFixation.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(newFixation.getOrderRefNo()).isEqualTo("HR/INC/2026/001");

        verify(serviceBookRepository).save(any());
    }

    @Test
    void processBatch_withheldEmployee_skipsWithoutTouchingFixations() {
        IncrementDueEntry withheld = new IncrementDueEntry(
                100L, "2801", "Test Employee", "E3", new BigDecimal("61800.00"), new BigDecimal("1850"),
                new BigDecimal("63650.00"), false, true, "Active disciplinary case");
        doReturn(List.of(withheld)).when(service).dueList(any());

        IncrementBatchProcessRequest request = new IncrementBatchProcessRequest(
                List.of(100L), "HR/INC/2026/001", LocalDate.of(2026, 4, 1), null);
        IncrementBatchProcessResponse response = service.processBatch(request);

        assertThat(response.processedCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.skippedReasons().get(0)).contains("withheld");
        verify(regularPayFixationRepository, never()).save(any());
        verify(employmentCategoryRepository, never()).findByEmployeeId(eq(100L));
    }

    @Test
    void processBatch_employeeNotInDueList_isSkippedAsIneligible() {
        doReturn(List.of()).when(service).dueList(any());

        IncrementBatchProcessRequest request = new IncrementBatchProcessRequest(
                List.of(999L), "HR/INC/2026/001", LocalDate.of(2026, 4, 1), null);
        IncrementBatchProcessResponse response = service.processBatch(request);

        assertThat(response.processedCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(response.skippedReasons().get(0)).contains("not eligible");
    }
}
