package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfCeilingOperator;
import in.gov.jci.hrms.entity.CpfCeilingSourceMetric;
import in.gov.jci.hrms.entity.CpfFrequencyScope;
import in.gov.jci.hrms.entity.CpfRepaymentCreditMethod;
import in.gov.jci.hrms.entity.CpfRuleStatus;
import in.gov.jci.hrms.entity.CpfWithdrawalPurposeMaster;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleDetail;
import in.gov.jci.hrms.entity.CpfWithdrawalRuleVersion;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.repository.CpfWithdrawalPurposeMasterRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalRuleDetailRepository;
import in.gov.jci.hrms.repository.CpfWithdrawalRuleVersionRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises CpfWithdrawalRuleEngine against the CPF withdrawal rules ALREADY LIVE on the shared dev
 * database (MARRIAGE/MEDICAL_EMERGENCY/HOUSING_LOAN_REPAYMENT - see CpfHeadMaster's own javadoc for how
 * this schema was discovered) - proving the engine reads real, admin-configured rule rows rather than any
 * hardcoded business value, using the exact figures those rules already carry.
 */
@SpringBootTest
@Transactional
class CpfWithdrawalRuleEngineTest {

    @Autowired private CpfWithdrawalRuleEngine ruleEngine;
    @Autowired private CpfWithdrawalRuleVersionRepository versionRepository;
    @Autowired private CpfWithdrawalRuleDetailRepository detailRepository;
    @Autowired private CpfWithdrawalPurposeMasterRepository purposeRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFRULE", "CPF Rule Engine Test Dept"));
        designation = designationRepository.save(new Designation("CPF Rule Engine Test Officer"));
    }

    private CpfWithdrawalRuleDetail activeDetailFor(String purposeCode) {
        CpfWithdrawalRuleVersion version = versionRepository
                .findFirstByPurpose_IdAndStatusOrderByEffectiveFromDesc(purposeRepository.findByCode(purposeCode).orElseThrow().getId(), CpfRuleStatus.APPROVED)
                .orElseThrow(() -> new AssertionError("No APPROVED version live for purpose " + purposeCode + " - expected the seeded data to have one"));
        return detailRepository.findByVersion_Id(version.getId()).orElseThrow();
    }

    @Test
    void marriage_fiftyPercentOfEligibleAAndB_headCLocked() {
        CpfWithdrawalRuleDetail detail = activeDetailFor("MARRIAGE");

        assertThat(detail.getMinServiceMonths()).isEqualTo(84); // 7 years, per the live seed
        var eligibleHeads = ruleEngine.resolveEligibleHeadsOrdered(detail);
        assertThat(eligibleHeads).extracting(h -> h.getHead().getCode()).containsExactly("HEAD_B", "HEAD_A");

        Map<String, BigDecimal> balances = Map.of("HEAD_A", new BigDecimal("100000"), "HEAD_B", new BigDecimal("50000"), "HEAD_C", new BigDecimal("80000"));
        var ceiling = ruleEngine.evaluateCeiling(detail, new CpfWithdrawalRuleEngine.CeilingEvaluationContext(null, balances, null, null, null));

        // Eligible balance = A+B only (C locked) = 150000; 50% of that = 75000.
        assertThat(ceiling.totalEligibleBalance()).isEqualByComparingTo("150000");
        assertThat(ceiling.finalAmount()).isEqualByComparingTo("75000");

        var allocation = ruleEngine.allocateDebitAcrossHeads(detail, ceiling.finalAmount(), balances);
        // HEAD_B (priority 1) debited first, fully (50000); remaining 25000 comes from HEAD_A.
        assertThat(allocation.debitByHeadCode().get("HEAD_B")).isEqualByComparingTo("50000");
        assertThat(allocation.debitByHeadCode().get("HEAD_A")).isEqualByComparingTo("25000");
        assertThat(allocation.debitByHeadCode().getOrDefault("HEAD_C", BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void medicalEmergency_minOfSixMonthsWagesAndFullBalance_noServiceRequirement() {
        CpfWithdrawalRuleDetail detail = activeDetailFor("MEDICAL_EMERGENCY");
        assertThat(detail.getMinServiceMonths()).isZero();

        Map<String, BigDecimal> balances = Map.of("HEAD_A", new BigDecimal("40000"), "HEAD_B", new BigDecimal("10000"), "HEAD_C", new BigDecimal("30000"));
        // 6 months wages = 6*8000 = 48000; full eligible balance (A+B, C locked) = 50000 -> MIN = 48000.
        var ceiling = ruleEngine.evaluateCeiling(detail, new CpfWithdrawalRuleEngine.CeilingEvaluationContext(
                new BigDecimal("8000"), balances, null, null, null));

        assertThat(ceiling.totalEligibleBalance()).isEqualByComparingTo("50000");
        assertThat(ceiling.finalAmount()).isEqualByComparingTo("48000");
    }

    @Test
    void housingLoanRepayment_headCEligible_balanceRetentionCapsWithdrawal() {
        CpfWithdrawalRuleDetail detail = activeDetailFor("HOUSING_LOAN_REPAYMENT");
        assertThat(detail.getBalanceRetentionPct()).isEqualByComparingTo("25.00");
        var eligibleHeads = ruleEngine.resolveEligibleHeadsOrdered(detail);
        assertThat(eligibleHeads).hasSize(3); // A+B+C all eligible for housing

        Map<String, BigDecimal> balances = Map.of("HEAD_A", new BigDecimal("100000"), "HEAD_B", new BigDecimal("50000"), "HEAD_C", new BigDecimal("150000"));
        // Total eligible = 300000. With a large basicPlusDa/outstandingLoan so neither of those two
        // components binds, MIN(36*wages, 100%*outstandingLoan, 100%*eligibleBalance) = eligibleBalance =
        // 300000; then 25% mandatory retention caps the actual withdrawal at 75% of that = 225000.
        // outstandingLoan (not propertyCost) is this purpose's real ceiling metric - see V86, which
        // corrected the seeded OUTSTANDING_LOAN_CAP component off an earlier PROPERTY_COST mistake.
        var ceiling = ruleEngine.evaluateCeiling(detail, new CpfWithdrawalRuleEngine.CeilingEvaluationContext(
                new BigDecimal("50000"), balances, null, null, new BigDecimal("1000000")));

        assertThat(ceiling.totalEligibleBalance()).isEqualByComparingTo("300000");
        assertThat(ceiling.finalAmount()).isEqualByComparingTo("225000");
    }

    @Test
    void serviceEligibility_includesPriorQualifyingServiceWhenConfigured() {
        CpfWithdrawalRuleDetail detail = activeDetailFor("MARRIAGE"); // 84 months required, includePreviousService=true
        Employee employee = employeeRepository.save(new Employee("EMP-RULE-1", "Rule", "Tester", "rule1@example.com",
                LocalDate.now().minusMonths(60), department, designation));
        employee.setPriorQualifyingServiceDays(365 * 3); // 3 prior years
        employeeRepository.save(employee);

        var result = ruleEngine.evaluateServiceEligibility(detail, employee, LocalDate.now());
        // 60 months own service + ~36 months prior = ~96 months >= 84 required.
        assertThat(result.eligible()).isTrue();
    }

    @Test
    void frequencyAndConcurrency_noApplicationsYet_isEligible() {
        CpfWithdrawalRuleDetail detail = activeDetailFor("MARRIAGE");
        var result = ruleEngine.evaluateFrequency(detail, "NO-SUCH-EMPLOYEE-CODE",
                purposeRepository.findByCode("MARRIAGE").orElseThrow().getId(), LocalDate.now());
        assertThat(result.eligible()).isTrue();
        assertThat(result.usedCount()).isZero();
    }
}
