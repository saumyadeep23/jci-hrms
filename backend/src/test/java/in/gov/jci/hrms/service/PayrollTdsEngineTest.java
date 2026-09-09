package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeFinancialYearId;
import in.gov.jci.hrms.entity.EmployeeTaxRegime;
import in.gov.jci.hrms.entity.PayrollTaxOverride;
import in.gov.jci.hrms.entity.PayrollTaxSlab;
import in.gov.jci.hrms.entity.TaxOverrideScope;
import in.gov.jci.hrms.entity.TaxRegimeType;
import in.gov.jci.hrms.entity.ViewEmployeeTaxYtdAggregate;
import in.gov.jci.hrms.repository.EmployeeTaxRegimeRepository;
import in.gov.jci.hrms.repository.PayrollStatutoryParameterRepository;
import in.gov.jci.hrms.repository.PayrollTaxOverrideRepository;
import in.gov.jci.hrms.repository.PayrollTaxSlabRepository;
import in.gov.jci.hrms.repository.ViewEmployeeTaxYtdAggregateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollTdsEngineTest {

    @Mock
    private ViewEmployeeTaxYtdAggregateRepository ytdAggregateRepository;
    @Mock
    private EmployeeTaxRegimeRepository employeeTaxRegimeRepository;
    @Mock
    private PayrollTaxSlabRepository payrollTaxSlabRepository;
    @Mock
    private PayrollTaxOverrideRepository payrollTaxOverrideRepository;
    @Mock
    private PayrollStatutoryParameterRepository payrollStatutoryParameterRepository;

    private PayrollTdsEngine payrollTdsEngine;
    private Employee employee;

    @BeforeEach
    void setUp() {
        payrollTdsEngine = new PayrollTdsEngine(ytdAggregateRepository, employeeTaxRegimeRepository,
                payrollTaxSlabRepository, payrollTaxOverrideRepository, payrollStatutoryParameterRepository);

        Department department = new Department("ENG", "Engineering");
        Designation designation = new Designation("Manager");
        employee = new Employee("EMP-001", "Asha", "Rao", "asha.rao@example.com",
                LocalDate.of(2020, 1, 15), department, designation);
        ReflectionTestUtils.setField(employee, "id", 1L);
    }

    /** PayrollTaxSlab/ViewEmployeeTaxYtdAggregate only expose a protected no-arg constructor (JPA/Hibernate-only) - reflection is the only way to build one from a test in another package. */
    private static <T> T instantiate(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate " + type, e);
        }
    }

    private List<PayrollTaxSlab> newRegimeSlabs() {
        PayrollTaxSlab slab1 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab1, "slabMin", new BigDecimal("0.00"));
        ReflectionTestUtils.setField(slab1, "slabMax", new BigDecimal("300000.00"));
        ReflectionTestUtils.setField(slab1, "taxRate", new BigDecimal("0.00"));
        ReflectionTestUtils.setField(slab1, "cessRate", new BigDecimal("4.00"));

        PayrollTaxSlab slab2 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab2, "slabMin", new BigDecimal("300000.00"));
        ReflectionTestUtils.setField(slab2, "slabMax", new BigDecimal("700000.00"));
        ReflectionTestUtils.setField(slab2, "taxRate", new BigDecimal("5.00"));
        ReflectionTestUtils.setField(slab2, "cessRate", new BigDecimal("4.00"));

        PayrollTaxSlab slab3 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab3, "slabMin", new BigDecimal("700000.00"));
        ReflectionTestUtils.setField(slab3, "slabMax", new BigDecimal("1000000.00"));
        ReflectionTestUtils.setField(slab3, "taxRate", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(slab3, "cessRate", new BigDecimal("4.00"));

        PayrollTaxSlab slab4 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab4, "slabMin", new BigDecimal("1000000.00"));
        ReflectionTestUtils.setField(slab4, "slabMax", new BigDecimal("1200000.00"));
        ReflectionTestUtils.setField(slab4, "taxRate", new BigDecimal("15.00"));
        ReflectionTestUtils.setField(slab4, "cessRate", new BigDecimal("4.00"));

        PayrollTaxSlab slab5 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab5, "slabMin", new BigDecimal("1200000.00"));
        ReflectionTestUtils.setField(slab5, "slabMax", new BigDecimal("1500000.00"));
        ReflectionTestUtils.setField(slab5, "taxRate", new BigDecimal("20.00"));
        ReflectionTestUtils.setField(slab5, "cessRate", new BigDecimal("4.00"));

        PayrollTaxSlab slab6 = instantiate(PayrollTaxSlab.class);
        ReflectionTestUtils.setField(slab6, "slabMin", new BigDecimal("1500000.00"));
        ReflectionTestUtils.setField(slab6, "slabMax", null);
        ReflectionTestUtils.setField(slab6, "taxRate", new BigDecimal("30.00"));
        ReflectionTestUtils.setField(slab6, "cessRate", new BigDecimal("4.00"));

        return List.of(slab1, slab2, slab3, slab4, slab5, slab6);
    }

    private ViewEmployeeTaxYtdAggregate ytdAggregate(BigDecimal cumulativeGross, BigDecimal cumulativeTdsPaid) {
        ViewEmployeeTaxYtdAggregate aggregate = instantiate(ViewEmployeeTaxYtdAggregate.class);
        ReflectionTestUtils.setField(aggregate, "id", new EmployeeFinancialYearId(1L, "2026-2027"));
        ReflectionTestUtils.setField(aggregate, "totalCumulativeGross", cumulativeGross);
        ReflectionTestUtils.setField(aggregate, "totalCumulativeTdsPaid", cumulativeTdsPaid);
        return aggregate;
    }

    // ---- remainingMonths ----

    @Test
    void remainingMonths_forAprilToDecember_countsToMarch() {
        assertThat(payrollTdsEngine.remainingMonths(4)).isEqualTo(12); // Apr..Mar inclusive
        assertThat(payrollTdsEngine.remainingMonths(12)).isEqualTo(4); // Dec, Jan, Feb, Mar
    }

    @Test
    void remainingMonths_forJanuaryToMarch_countsWithinCalendarYear() {
        assertThat(payrollTdsEngine.remainingMonths(1)).isEqualTo(3);
        assertThat(payrollTdsEngine.remainingMonths(3)).isEqualTo(1);
    }

    // ---- cumulative projection distributes remaining tax over remaining months ----

    @Test
    void computeMonthlyTds_distributesRemainingProjectedTaxAcrossRemainingMonths() {
        // August (month 8 of FY starting April): remainingMonths = 12 - 8 + 4 = 8.
        when(ytdAggregateRepository.findById(new EmployeeFinancialYearId(1L, "2026-2027")))
                .thenReturn(Optional.of(ytdAggregate(new BigDecimal("400000.00"), new BigDecimal("2000.00"))));
        when(employeeTaxRegimeRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollTaxSlabRepository.findByFinancialYearAndRegimeOrderBySlabMinAsc("2026-2027", TaxRegimeType.NEW))
                .thenReturn(newRegimeSlabs());
        when(payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(1L, 2026, 8)).thenReturn(Optional.empty());

        // currentGrossAmount = regularMonthlyGross = 100000; projectedAnnualGross = 400000 + 100000 + 100000*7 = 1,200,000.
        PayrollTdsEngine.TdsResult result = payrollTdsEngine.computeMonthlyTds(employee, "2026-2027", 8, 2026,
                new BigDecimal("100000.00"), new BigDecimal("100000.00"), new BigDecimal("60000.00"), false);

        assertThat(result.remainingMonths()).isEqualTo(8);
        assertThat(result.regime()).isEqualTo(TaxRegimeType.NEW);
        assertThat(result.projectedAnnualGross()).isEqualByComparingTo("1200000.00");

        // Taxable income (default NEW regime, no NPS exemption): 1,200,000 - 75,000 = 1,125,000.
        // Slab tax: 0 (0-3L) + 5%*(4L) = 20,000 + 10%*(3L) = 30,000 + 15%*(1.25L) = 18,750 = 68,750.
        // Cess 4% = 2750. Total tax = 71,500.
        assertThat(result.projectedAnnualTax()).isEqualByComparingTo("71500.00");

        // Remaining tax after cumulative 2000 already paid: 69,500 / 8 months = 8,687.50.
        assertThat(result.calculatedAmount()).isEqualByComparingTo("8687.50");
        assertThat(result.finalAmount()).isEqualByComparingTo("8687.50");
        assertThat(result.overridden()).isFalse();
    }

    @Test
    void computeMonthlyTds_separatesCurrentGrossFromRegularMonthlyGrossForProjection() {
        // Head 20 leave encashment inflates THIS month's gross but must not inflate the (N-1)
        // future-months projection - regularMonthlyGross (excluding the encashment) is what's
        // multiplied by (N-1), while currentGrossAmount (including it) is what's added once.
        when(ytdAggregateRepository.findById(new EmployeeFinancialYearId(1L, "2026-2027"))).thenReturn(Optional.empty());
        when(employeeTaxRegimeRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollTaxSlabRepository.findByFinancialYearAndRegimeOrderBySlabMinAsc("2026-2027", TaxRegimeType.NEW))
                .thenReturn(newRegimeSlabs());
        when(payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(1L, 2026, 8)).thenReturn(Optional.empty());

        BigDecimal currentGrossWithEncashment = new BigDecimal("150000.00"); // 100000 regular + 50000 Head 20
        BigDecimal regularMonthlyGross = new BigDecimal("100000.00");

        PayrollTdsEngine.TdsResult result = payrollTdsEngine.computeMonthlyTds(employee, "2026-2027", 8, 2026,
                currentGrossWithEncashment, regularMonthlyGross, new BigDecimal("60000.00"), false);

        // projectedAnnualGross = 0 + 150000 + 100000*7 = 850,000 - NOT 150000*7 = 1,050,000 + 150000.
        assertThat(result.projectedAnnualGross()).isEqualByComparingTo("850000.00");
    }

    // ---- TDS override replaces computed tax ----

    @Test
    void computeMonthlyTds_whenOverridePresent_usesOverriddenAmountAndFlagsOverridden() {
        when(ytdAggregateRepository.findById(new EmployeeFinancialYearId(1L, "2026-2027"))).thenReturn(Optional.empty());
        when(employeeTaxRegimeRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollTaxSlabRepository.findByFinancialYearAndRegimeOrderBySlabMinAsc("2026-2027", TaxRegimeType.NEW))
                .thenReturn(newRegimeSlabs());

        PayrollTaxOverride override = new PayrollTaxOverride(employee, "2026-2027", 8, 2026,
                new BigDecimal("0.00"), new BigDecimal("5000.00"), "Bill Section correction for arrears", "bill.officer",
                TaxOverrideScope.MONTH_ONLY);
        when(payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(1L, 2026, 8))
                .thenReturn(Optional.of(override));

        PayrollTdsEngine.TdsResult result = payrollTdsEngine.computeMonthlyTds(employee, "2026-2027", 8, 2026,
                new BigDecimal("100000.00"), new BigDecimal("100000.00"), new BigDecimal("60000.00"), false);

        assertThat(result.overridden()).isTrue();
        assertThat(result.finalAmount()).isEqualByComparingTo("5000.00");
        // calculatedAmount still reflects the engine's own computation, distinct from the override.
        assertThat(result.calculatedAmount()).isNotEqualByComparingTo(result.finalAmount());
    }

    @Test
    void computeMonthlyTds_belowRebateThreshold_newRegimeZerosOutTax() {
        when(ytdAggregateRepository.findById(new EmployeeFinancialYearId(1L, "2026-2027"))).thenReturn(Optional.empty());
        when(employeeTaxRegimeRepository.findByEmployee_IdAndFinancialYear(1L, "2026-2027")).thenReturn(Optional.empty());
        when(payrollTaxSlabRepository.findByFinancialYearAndRegimeOrderBySlabMinAsc("2026-2027", TaxRegimeType.NEW))
                .thenReturn(newRegimeSlabs());
        when(payrollTaxOverrideRepository.findByEmployee_IdAndPayrollYearAndPayrollMonth(1L, 2026, 4)).thenReturn(Optional.empty());

        // 50000/month * 12 = 600,000 taxable pre-deduction; minus 75000 standard deduction = 525,000 <= 700,000 rebate threshold.
        PayrollTdsEngine.TdsResult result = payrollTdsEngine.computeMonthlyTds(employee, "2026-2027", 4, 2026,
                new BigDecimal("50000.00"), new BigDecimal("50000.00"), new BigDecimal("30000.00"), false);

        assertThat(result.finalAmount()).isEqualByComparingTo("0.00");
    }
}
