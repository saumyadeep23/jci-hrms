package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfApplicationDocumentSubmission;
import in.gov.jci.hrms.dto.CpfApplicationRequest;
import in.gov.jci.hrms.dto.CpfApplicationResponse;
import in.gov.jci.hrms.dto.CpfApplicationSanctionRequest;
import in.gov.jci.hrms.entity.CpfApplicationLedgerAllocation;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfApplicationLedgerAllocationRepository;
import in.gov.jci.hrms.repository.CpfLoanApplicationRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The full apply -&gt; sanction -&gt; disburse lifecycle for the rule-driven CpfApplication flow, proving
 * disbursement both (a) allocates per-head via CpfWithdrawalRuleEngine (Part 9) into
 * CpfApplicationLedgerAllocation and (b) posts one real LOAN_WITHDRAWAL entry to
 * cpf_trust_member_ledger_entries - the single authoritative balance ledger (Part 34: no second, competing
 * balance engine). Uses the live-seeded MEDICAL_EMERGENCY rule (0 months service, NONE frequency scope -
 * fewest preconditions) against a freshly-created test employee with a synthetic opening balance.
 */
@SpringBootTest
@Transactional
class CpfApplicationServiceTest {

    @Autowired private CpfApplicationService applicationService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfApplicationLedgerAllocationRepository allocationRepository;
    @Autowired private CpfLoanApplicationRepository loanApplicationRepository;
    @Autowired private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Autowired private RegularPayFixationRepository regularPayFixationRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFAPP", "CPF Application Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Application Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFAPP-1", "App", "Tester", "cpfapp1@example.com",
                LocalDate.now().minusYears(2), department, designation));

        // (employee, finYear, valueDate, entryType, runningEeBalance, runningErBalance, runningVpfBalance, runningTotalBalance)
        // EE(HEAD_A)=80000, ER(HEAD_C, locked for MEDICAL_EMERGENCY)=20000, VPF(HEAD_B)=15000 - deliberately
        // less than the eligible ceiling so a disbursement actually spills from HEAD_B into HEAD_A (Part 9).
        CpfTrustMemberLedgerEntry opening = new CpfTrustMemberLedgerEntry(employee, "2025-2026", LocalDate.now().minusMonths(1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("80000.00"), new BigDecimal("20000.00"), new BigDecimal("15000.00"),
                new BigDecimal("115000.00"));
        ledgerRepository.save(opening);

        // checkEligibility() now resolves basicPlusDa server-side from the employee's current
        // regular_pay_fixations row + the current da_rate_history rate for its grade scale's scale_type
        // (Task 4), rather than trusting the caller. Seeded against a fresh CDA-type scale specifically
        // because this dev DB has no da_rate_history row for CDA at all - resolveCurrentBasicPlusDa's own
        // "no rate configured -> DA=0" fallback then makes basicPlusDa exactly equal basicPay (6000.00),
        // matching this suite's long-standing "6*6000=36000" ceiling assumption below without needing to
        // hardcode (and keep in sync with) whatever IDA's real current DA percentage happens to be.
        GradeScaleMaster cdaScale = gradeScaleMasterRepository.save(seedCdaScale("CPFAPPTST", 9001));
        regularPayFixationRepository.save(new RegularPayFixation(employee, cdaScale, new BigDecimal("6000.00"), LocalDate.now().minusYears(1)));
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
    void applySanctionDisburse_medicalEmergency_postsRealLedgerWithdrawalAndPerHeadAllocation() {
        CpfApplicationResponse applied = applicationService.apply(
                new CpfApplicationRequest("EMP-CPFAPP-1", "MEDICAL_EMERGENCY", new BigDecimal("30000"), new BigDecimal("6000"), null, null,
                        medicalEmergencyDocuments(), null),
                "tester");
        assertThat(applied.status()).isEqualTo(CpfApplicationStatus.APPLIED);
        // Eligible = MIN(6*6000=36000, eligible balance A+B=100000) = 36000; applied 30000 <= 36000, so it's accepted.
        assertThat(applied.eligibleAmount()).isEqualByComparingTo("36000");

        CpfApplicationResponse sanctioned = applicationService.sanction(UUID.fromString(applied.id()),
                new CpfApplicationSanctionRequest(new BigDecimal("30000"), null, new BigDecimal("6000"), null, null, null));
        assertThat(sanctioned.status()).isEqualTo(CpfApplicationStatus.SANCTIONED);
        assertThat(sanctioned.tenureMonths()).isNull(); // MEDICAL_EMERGENCY has no interest_method -> no tenure/EMI

        CpfApplicationResponse disbursed = applicationService.disburse(UUID.fromString(applied.id()));
        assertThat(disbursed.status()).isEqualTo(CpfApplicationStatus.DISBURSED);

        // Head allocation: MEDICAL_EMERGENCY debits HEAD_B (VPF, priority 1) first, then HEAD_A (EE) - HEAD_C locked.
        List<CpfApplicationLedgerAllocation> allocations = allocationRepository.findByApplication_Id(UUID.fromString(applied.id()));
        assertThat(allocations).hasSize(2);
        var vpfAllocation = allocations.stream().filter(a -> a.getHead().getCode().equals("HEAD_B")).findFirst().orElseThrow();
        var eeAllocation = allocations.stream().filter(a -> a.getHead().getCode().equals("HEAD_A")).findFirst().orElseThrow();
        assertThat(vpfAllocation.getAllocatedAmount()).isEqualByComparingTo("15000"); // full VPF balance, exhausted first (priority 1)
        assertThat(eeAllocation.getAllocatedAmount()).isEqualByComparingTo("15000"); // remaining 30000-15000 spills into EE (priority 2)

        // The real ledger gained exactly one new LOAN_WITHDRAWAL row reflecting the same split.
        List<CpfTrustMemberLedgerEntry> ledgerRows = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry withdrawal = ledgerRows.get(ledgerRows.size() - 1);
        assertThat(withdrawal.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_WITHDRAWAL);
        assertThat(withdrawal.getEeShareDebit()).isEqualByComparingTo("15000.00");
        assertThat(withdrawal.getVpfDebit()).isEqualByComparingTo("15000.00");
        assertThat(withdrawal.getErShareDebit()).isEqualByComparingTo("0.00"); // HEAD_C never touched
        assertThat(withdrawal.getRunningEeBalance()).isEqualByComparingTo("65000.00"); // 80000-15000
        assertThat(withdrawal.getRunningVpfBalance()).isEqualByComparingTo("0.00"); // 15000-15000
        assertThat(withdrawal.getRunningErBalance()).isEqualByComparingTo("20000.00"); // untouched
    }

    @Test
    void apply_amountExceedingCeiling_isRejected() {
        assertThatThrownBy(() -> applicationService.apply(
                new CpfApplicationRequest("EMP-CPFAPP-1", "MEDICAL_EMERGENCY", new BigDecimal("999999"), new BigDecimal("6000"), null, null, null, null),
                "tester"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exceeds the maximum eligible amount");
    }

    @Test
    void checkEligibility_noRuleConfiguredForPurpose_throwsRatherThanFabricatingACeiling() {
        assertThatThrownBy(() -> applicationService.checkEligibility("EMP-CPFAPP-1", "MARRIAGE_MISSPELLED_PURPOSE", null, null, null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void apply_missingMandatoryDocument_isRejected() {
        assertThatThrownBy(() -> applicationService.apply(
                new CpfApplicationRequest("EMP-CPFAPP-1", "MEDICAL_EMERGENCY", new BigDecimal("30000"), new BigDecimal("6000"), null, null, null, null),
                "tester"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Missing mandatory supporting document");
    }

    /** MEDICAL_EMERGENCY's own live-seeded CpfRuleDocument rows (Part 29) - both mandatory. */
    private List<CpfApplicationDocumentSubmission> medicalEmergencyDocuments() {
        return List.of(
                new CpfApplicationDocumentSubmission("Doctor / Specialist Certificate of Major Illness", "s3/test/doctor-cert.pdf", "doctor-cert.pdf"),
                new CpfApplicationDocumentSubmission("Hospital Estimate / Treatment Bills", "s3/test/hospital-bill.pdf", "hospital-bill.pdf"));
    }

    /**
     * HOUSING_LOAN_REPAYMENT's own ceiling is MIN(36*BasicPlusDA, outstanding housing loan, eligible
     * balance). min service = 120 months, hence the separate 11-year-tenure employee.
     *
     * <p>Task 4 Part 12: a client-supplied outstandingLoan is no longer trusted for this purpose at all -
     * checkEligibility() now always resolves it server-side from the employee's own real, non-CLOSED
     * CpfLoanApplication housing loans (never a second balance table). The first assertion below passes
     * a (now-ignored) client value of 50000 while no real loan exists, proving it truly has no effect;
     * the second creates a real loan and proves THAT is what feeds the ceiling. basicPlusDa is likewise
     * now always server-resolved (resolveCurrentBasicPlusDa) - the client-supplied 10000 passed below is
     * equally ignored; a current pay fixation with basicPay=10000 (against a fresh CDA scale, so DA
     * resolves to 0 - see setUp()'s own comment) is seeded instead so the 36x multiplier this test's own
     * math comment describes still holds against a real figure.
     */
    @Test
    void checkEligibility_housingLoanRepayment_outstandingLoanIsServerResolved_notClientSupplied() {
        Department department = departmentRepository.save(new Department("CPFHLR", "CPF Housing Loan Repayment Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Housing Loan Repayment Test Officer"));
        Employee pendingLongServiceEmployee = new Employee("EMP-CPFHLR-1", "HousingLoan", "Tester", "cpfhlr1@example.com",
                LocalDate.now().minusYears(11), department, designation);
        // The 7-arg convenience constructor hardcodes cpfAcNo="CPF00001"/panNumber="ABCDE1234F" (fine for
        // single-employee fixtures elsewhere) - setUp() already created one such employee, so this one
        // needs distinct values to satisfy uq_employees_cpf_ac_no_active / uq_employees_pan_number_active.
        pendingLongServiceEmployee.setCpfAcNo("CPF00002");
        pendingLongServiceEmployee.setPanNumber("BBCDE1234F");
        Employee longServiceEmployee = employeeRepository.save(pendingLongServiceEmployee);
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(longServiceEmployee, "2025-2026", LocalDate.now().minusMonths(1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("200000.00"), BigDecimal.ZERO, new BigDecimal("50000.00"),
                new BigDecimal("250000.00")));
        GradeScaleMaster hlrCdaScale = gradeScaleMasterRepository.save(seedCdaScale("CPFHLRTST", 9002));
        regularPayFixationRepository.save(new RegularPayFixation(longServiceEmployee, hlrCdaScale, new BigDecimal("10000.00"), LocalDate.now().minusYears(1)));

        var beforeRealLoanExists = applicationService.checkEligibility("EMP-CPFHLR-1", "HOUSING_LOAN_REPAYMENT",
                new BigDecimal("10000"), null, null, new BigDecimal("50000"));
        assertThat(beforeRealLoanExists.eligibleAmount()).isEqualByComparingTo("0"); // client-supplied 50000 is ignored - no real loan yet

        CpfLoanApplication realHousingLoan = new CpfLoanApplication("CPFL/TEST/HLR-1", longServiceEmployee,
                CpfLoanType.REFUNDABLE_LOAN, "HOUSING_LOAN_REPAYMENT", new BigDecimal("50000"));
        realHousingLoan.setOutstandingBalance(new BigDecimal("50000"));
        loanApplicationRepository.save(realHousingLoan);

        var withRealLoan = applicationService.checkEligibility("EMP-CPFHLR-1", "HOUSING_LOAN_REPAYMENT",
                new BigDecimal("10000"), null, null, null); // no client value supplied at all - server resolves it regardless
        // MIN(36*10000=360000, real outstanding loan=50000, eligible balance 250000) = 50000, well under the 25% retention cap.
        assertThat(withRealLoan.eligibleAmount()).isEqualByComparingTo("50000");
    }
}
