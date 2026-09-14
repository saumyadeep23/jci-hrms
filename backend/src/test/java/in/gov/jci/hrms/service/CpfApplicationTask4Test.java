package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfApplicationDocumentSubmission;
import in.gov.jci.hrms.dto.CpfApplicationRequest;
import in.gov.jci.hrms.dto.CpfApplicationResponse;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.repository.CpfApplicationRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import in.gov.jci.hrms.security.CpfApplicationSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 4 (CPF Loans & Advances Simulator + Apply for Loan repair) - the structured eligibility fields
 * (ceiling component breakdown, head allocation preview, repayment preview) the Simulator/wizard read,
 * apply()'s new idempotency guard (uq_cpf_application_pending_per_purpose, V89), and the new
 * CpfApplicationSecurity self-access bean backing the widened EMPLOYEE RBAC. Uses the live-seeded
 * TEMPORARY_HARDSHIP rule (REFUNDABLE, 12 months min service, default tenure 24 months) since
 * MEDICAL_EMERGENCY (used by the sibling CpfApplicationServiceTest) carries no repayment schedule at all.
 */
@SpringBootTest
@Transactional
class CpfApplicationTask4Test {

    @Autowired private CpfApplicationService applicationService;
    @Autowired private CpfApplicationRepository applicationRepository;
    @Autowired private CpfApplicationSecurity applicationSecurity;
    @Autowired private CpfLoanRecoveryPolicyService recoveryPolicyService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Autowired private RegularPayFixationRepository regularPayFixationRepository;
    @Autowired private DaRateHistoryRepository daRateHistoryRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFT4", "CPF Task4 Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Task4 Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFT4-1", "Task4", "Tester", "cpft4-1@example.com",
                LocalDate.now().minusYears(3), department, designation));
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(employee, "2025-2026", LocalDate.now().minusMonths(1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("40000.00"),
                new BigDecimal("140000.00")));

        // checkEligibility() now resolves basicPlusDa server-side from the employee's current
        // regular_pay_fixations row + the current da_rate_history rate for its grade scale's scale_type
        // (Task 4) rather than trusting the caller - seeded against a fresh CDA-type scale specifically
        // because this dev DB has no da_rate_history row for CDA at all, so resolveCurrentBasicPlusDa's
        // own "no rate configured -> DA=0" fallback makes basicPlusDa exactly equal basicPay (10000.00),
        // matching this suite's tests below (all of which used to pass 10000 as a now-ignored client
        // value) without depending on whatever IDA's real current DA percentage happens to be. See
        // basicPlusDa_isResolvedFromRegularPayFixationAndCurrentIdaDaRate below for a test that exercises
        // the real percentage-rate multiplication against the live-seeded IDA rate instead.
        GradeScaleMaster cdaScale = gradeScaleMasterRepository.save(seedCdaScale("CPFT4TST", 9003));
        regularPayFixationRepository.save(new RegularPayFixation(employee, cdaScale, new BigDecimal("10000.00"), LocalDate.now().minusYears(1)));
    }

    /** hierarchy_level (like scale_code) is globally unique across grade_scale_master (15 real seeded rows
     * occupy 1-15) - callers must each pass a distinct level in the 9000s to avoid colliding with that
     * seed data or with each other when more than one is created in the same test's transaction. */
    private GradeScaleMaster seedCdaScale(String scaleCode, int hierarchyLevel) {
        GradeScaleMaster scale = new GradeScaleMaster(scaleCode, Cadre.STAFF, hierarchyLevel, false, new BigDecimal("1000.00"), new BigDecimal("100000.00"));
        scale.setScaleType(ScaleType.CDA);
        return scale;
    }

    @Test
    void checkEligibility_returnsStructuredCeilingComponentsAndHeadAllocation_notJustFreeTextTrace() {
        var eligibility = applicationService.checkEligibility(employee.getEmployeeCode(), "TEMPORARY_HARDSHIP",
                new BigDecimal("10000"), null, null, null, new BigDecimal("20000"), 24);

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.ceilingComponents()).isNotEmpty();
        eligibility.ceilingComponents().forEach(c -> {
            assertThat(c.componentName()).isNotBlank();
            assertThat(c.sourceMetric()).isNotBlank();
        });

        assertThat(eligibility.headAllocation()).isNotEmpty();
        // Ascending debit priority order (CpfRuleHeadEligibility.debitPriority) - never hardcoded VPF-then-EE.
        List<Integer> priorities = eligibility.headAllocation().stream().map(in.gov.jci.hrms.dto.HeadAllocationResponse::debitPriority).toList();
        assertThat(priorities).isSorted();
        eligibility.headAllocation().forEach(h -> assertThat(h.headCode()).isNotBlank());
    }

    /** Proves resolveCurrentBasicPlusDa() actually reads regular_pay_fixations (basic_pay) and
     * da_rate_history (the live-seeded, currently-active IDA rate) - not a hardcoded percentage - by
     * independently re-deriving the same figure from those two tables here and checking the ceiling
     * engine's own BASIC_PLUS_DA-sourced component matches it exactly. */
    @Test
    void checkEligibility_basicPlusDa_resolvedFromRegularPayFixationAndCurrentIdaDaRate_notHardcoded() {
        Department department = departmentRepository.save(new Department("CPFT4DA", "CPF Task4 DA Rate Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Task4 DA Rate Test Officer"));
        Employee pending = new Employee("EMP-CPFT4-DA1", "Task4Da", "Tester", "cpft4-da1@example.com",
                LocalDate.now().minusYears(3), department, designation);
        pending.setCpfAcNo("CPF00003");
        pending.setPanNumber("CBCDE1234F");
        Employee idaEmployee = employeeRepository.save(pending);
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(idaEmployee, "2025-2026", LocalDate.now().minusMonths(1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("500000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500000.00")));

        GradeScaleMaster idaScale = gradeScaleMasterRepository.save(new GradeScaleMaster("CPFT4IDA", Cadre.STAFF, 9004, false,
                new BigDecimal("1000.00"), new BigDecimal("100000.00"))); // defaults to ScaleType.IDA
        BigDecimal basicPay = new BigDecimal("20000.00");
        regularPayFixationRepository.save(new RegularPayFixation(idaEmployee, idaScale, basicPay, LocalDate.now().minusYears(1)));

        BigDecimal currentIdaRate = daRateHistoryRepository
                .findTopByScaleTypeAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(ScaleType.IDA, LocalDate.now())
                .map(in.gov.jci.hrms.entity.DaRateHistory::getDaPercentage)
                .orElseThrow(() -> new IllegalStateException("Dev DB has no active current IDA DA rate configured - seed one before running this test"));
        BigDecimal expectedBasicPlusDa = basicPay.add(
                basicPay.multiply(currentIdaRate).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));

        var eligibility = applicationService.checkEligibility(idaEmployee.getEmployeeCode(), "TEMPORARY_HARDSHIP",
                null, null, null, null, null, null);

        var basicPlusDaComponent = eligibility.ceilingComponents().stream()
                .filter(c -> "BASIC_PLUS_DA".equals(c.sourceMetric()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("TEMPORARY_HARDSHIP's rule has no BASIC_PLUS_DA-sourced ceiling component"));
        // SIX_MONTH_BASIC_DA_CAP = 6 * BasicPlusDA (this rule's own configured factor, confirmed via the
        // Simulator's own live browser verification, and the same "6*basicPlusDa" this suite's sibling
        // tests already assume elsewhere) - what this assertion actually proves is that the metric fed
        // into that multiplication is the server-resolved figure re-derived above from
        // regular_pay_fixations + da_rate_history, not a client-supplied or fabricated value.
        assertThat(basicPlusDaComponent.calculatedValue()).isEqualByComparingTo(expectedBasicPlusDa.multiply(BigDecimal.valueOf(6)));
    }

    @Test
    void checkEligibility_repaymentPreview_usesConfiguredPolicyRatio_notHardcoded12() {
        var eligibility = applicationService.checkEligibility(employee.getEmployeeCode(), "TEMPORARY_HARDSHIP",
                new BigDecimal("10000"), null, null, null, new BigDecimal("12000"), 24);

        assertThat(eligibility.repaymentPreview()).isNotNull();
        var preview = eligibility.repaymentPreview();
        assertThat(preview.tenureMonths()).isEqualTo(24);
        assertThat(preview.principalInstallmentCount()).isEqualTo(24);
        assertThat(preview.interestPhaseStartInstallment()).isEqualTo(25); // principal-first: interest only after all 24 principal installments

        int ratio = recoveryPolicyService.currentOrThrow().getPrincipalInstallmentsPerInterestInstallment();
        int expectedInterestInstallments = (24 + ratio - 1) / ratio; // ceiling division, same formula resolveInterestInstallmentsFromPolicy uses
        assertThat(preview.interestInstallmentCount()).isEqualTo(expectedInterestInstallments);

        assertThat(preview.monthlyPrincipalInstallment()).isEqualByComparingTo(preview.principalAmount().divide(BigDecimal.valueOf(24), 2, java.math.RoundingMode.HALF_UP));
        assertThat(preview.totalRecovery()).isEqualByComparingTo(preview.principalAmount().add(preview.totalInterest()));
    }

    @Test
    void apply_doubleSubmit_isIdempotent_returnsTheSameApplication_neverCreatesADuplicateRow() {
        CpfApplicationRequest request = new CpfApplicationRequest(employee.getEmployeeCode(), "TEMPORARY_HARDSHIP",
                new BigDecimal("5000"), new BigDecimal("10000"), null, null,
                List.of(new CpfApplicationDocumentSubmission("Declaration of Temporary Hardship", "test/s3-key", "declaration.pdf")), null);

        CpfApplicationResponse first = applicationService.apply(request, "tester");
        CpfApplicationResponse retry = applicationService.apply(request, "tester");

        assertThat(retry.id()).isEqualTo(first.id());
        long pendingCount = applicationRepository.findByEmployeeCodeOrderByCreatedAtDesc(employee.getEmployeeCode()).stream()
                .filter(a -> a.getPurpose().getCode().equals("TEMPORARY_HARDSHIP") && a.getStatus() == CpfApplicationStatus.APPLIED)
                .count();
        assertThat(pendingCount).isEqualTo(1);
    }

    @Test
    void cpfApplicationSecurity_isSelf_trueForOwnEmployeeCode_falseOtherwise() {
        Authentication self = jwtFor(employee.getId());
        assertThat(applicationSecurity.isSelf(self, employee.getEmployeeCode())).isTrue();
        assertThat(applicationSecurity.isSelf(self, "NOT-" + employee.getEmployeeCode())).isFalse();

        Authentication someoneElse = jwtFor(employee.getId() + 999);
        assertThat(applicationSecurity.isSelf(someoneElse, employee.getEmployeeCode())).isFalse();

        assertThat(applicationSecurity.isSelf(null, employee.getEmployeeCode())).isFalse();
    }

    private Authentication jwtFor(Long employeeId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("employee_id", String.valueOf(employeeId))
                .claim("sub", "test-user")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of());
    }
}
