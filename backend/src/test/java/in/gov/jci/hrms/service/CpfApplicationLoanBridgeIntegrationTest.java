package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfApplicationDocumentSubmission;
import in.gov.jci.hrms.dto.CpfApplicationRequest;
import in.gov.jci.hrms.dto.CpfApplicationResponse;
import in.gov.jci.hrms.dto.CpfApplicationSanctionRequest;
import in.gov.jci.hrms.entity.CpfApplication;
import in.gov.jci.hrms.entity.CpfApplicationStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfLoanApplication;
import in.gov.jci.hrms.entity.CpfLoanApplicationStatus;
import in.gov.jci.hrms.entity.CpfLoanRecoveryPhase;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfApplicationLedgerAllocationRepository;
import in.gov.jci.hrms.repository.CpfApplicationRepository;
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
 * Task 3 (integration/hardening): closes the gap between the rule-engine CpfApplication flow and the
 * pre-existing CpfLoanApplication repayment machinery (Parts 1-9). Uses the live-seeded TEMPORARY_HARDSHIP
 * rule (the only REFUNDABLE purpose configured today - min service 12 months, ceiling MIN(6*BasicPlusDA,
 * 80% eligible balance), heads B then A, tenure 12-60/default 24) and MEDICAL_EMERGENCY (NON_REFUNDABLE,
 * 0 months service) as the negative control.
 */
@SpringBootTest
@Transactional
class CpfApplicationLoanBridgeIntegrationTest {

    @Autowired private CpfApplicationService applicationService;
    @Autowired private CpfApplicationRepository applicationRepository;
    @Autowired private CpfLoanApplicationRepository loanApplicationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfApplicationLedgerAllocationRepository allocationRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Autowired private RegularPayFixationRepository regularPayFixationRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFBRIDGE", "CPF Bridge Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Bridge Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFBRIDGE-1", "Bridge", "Tester", "cpfbridge1@example.com",
                LocalDate.now().minusYears(2), department, designation));

        // EE(HEAD_A)=100000, ER(HEAD_C, locked)=20000, VPF(HEAD_B)=30000 - applied amount is chosen below
        // to exhaust HEAD_B (priority 1) and spill the remainder into HEAD_A (priority 2), matching the
        // same debit-priority proof pattern already established for CpfApplicationServiceTest.
        CpfTrustMemberLedgerEntry opening = new CpfTrustMemberLedgerEntry(employee, "2025-2026", LocalDate.now().minusMonths(1),
                CpfLedgerEntryType.OPENING_BALANCE, new BigDecimal("100000.00"), new BigDecimal("20000.00"), new BigDecimal("30000.00"),
                new BigDecimal("150000.00"));
        ledgerRepository.save(opening);

        // checkEligibility() now resolves basicPlusDa server-side from the employee's current
        // regular_pay_fixations row + the current da_rate_history rate for its grade scale's scale_type
        // (Task 4) rather than trusting the caller - seeded against a fresh CDA-type scale specifically
        // because this dev DB has no da_rate_history row for CDA at all, so resolveCurrentBasicPlusDa's
        // own "no rate configured -> DA=0" fallback makes basicPlusDa exactly equal basicPay (10000.00),
        // matching this suite's tests below (all of which used to pass 10000/6000 as a now-ignored client
        // value) without depending on whatever IDA's real current DA percentage happens to be.
        GradeScaleMaster cdaScale = gradeScaleMasterRepository.save(seedCdaScale("CPFBRDGTST", 9005));
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

    /** TEMPORARY_HARDSHIP's own live-seeded CpfRuleDocument row (Part 29) - mandatory. */
    private List<CpfApplicationDocumentSubmission> temporaryHardshipDocuments() {
        return List.of(new CpfApplicationDocumentSubmission("Declaration of Temporary Hardship", "s3/test/hardship-declaration.pdf", "hardship-declaration.pdf"));
    }

    private CpfApplicationResponse applyAndSanctionTemporaryHardship(BigDecimal appliedAmount, Integer tenureMonths) {
        CpfApplicationResponse applied = applicationService.apply(
                new CpfApplicationRequest("EMP-CPFBRIDGE-1", "TEMPORARY_HARDSHIP", appliedAmount, new BigDecimal("10000"), null, null,
                        temporaryHardshipDocuments(), null),
                "tester");
        return applicationService.sanction(UUID.fromString(applied.id()),
                new CpfApplicationSanctionRequest(appliedAmount, tenureMonths, new BigDecimal("10000"), null, null, null));
    }

    @Test
    void refundableDisbursement_createsExactlyOneLinkedLoan_withFullSourceTraceabilityAndRepaymentSchedule() {
        CpfApplicationResponse sanctioned = applyAndSanctionTemporaryHardship(new BigDecimal("50000"), 24);
        UUID applicationId = UUID.fromString(sanctioned.id());

        CpfApplicationResponse disbursed = applicationService.disburse(applicationId);
        assertThat(disbursed.status()).isEqualTo(CpfApplicationStatus.DISBURSED);
        assertThat(disbursed.linkedLoanId()).isNotNull();

        List<CpfLoanApplication> loans = loanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId());
        assertThat(loans).hasSize(1);
        CpfLoanApplication loan = loans.get(0);

        // Source-of-origin link (Part 7): the loan can answer which application/purpose/rule created it.
        assertThat(loan.getCpfApplicationId()).isEqualTo(applicationId);
        assertThat(loan.getPurpose()).isEqualTo("TEMPORARY_HARDSHIP");
        assertThat(loan.getSanctionOrderNo()).isEqualTo(sanctioned.applicationNumber());

        // Sanctioned/disbursed amounts and repayment schedule basics (Part 41).
        assertThat(loan.getSanctionedAmount()).isEqualByComparingTo("50000.00");
        assertThat(loan.getOutstandingBalance()).isEqualByComparingTo("50000.00");
        assertThat(loan.getTotalInstallments()).isEqualTo(24);
        assertThat(loan.getMonthlyRecoveryPrincipal()).isEqualByComparingTo(new BigDecimal("50000.00").divide(new BigDecimal("24"), 2, java.math.RoundingMode.HALF_UP));
        assertThat(loan.getRecoveryPhase()).isEqualTo(CpfLoanRecoveryPhase.PRINCIPAL);
        assertThat(loan.getStatus()).isEqualTo(CpfLoanApplicationStatus.DISBURSED);

        // Interest-installment ratio came from policy, not a hardcoded 12 in this test or in application code.
        assertThat(loan.getTotalInterestInstallments()).isEqualTo(2); // 24 principal / 12-per-policy = 2
        assertThat(loan.getOutstandingInterest()).isEqualByComparingTo(loan.getTotalInterestAmount());
        assertThat(loan.getTotalInterestAmount().signum()).isPositive(); // interest_rate_annual=0.00 on the rule is NOT interest-free (Part 30)

        // Exactly one CPF ledger withdrawal, correctly split HEAD_B-then-HEAD_A (Part 9/12).
        List<CpfTrustMemberLedgerEntry> ledgerRows = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId());
        CpfTrustMemberLedgerEntry withdrawal = ledgerRows.get(ledgerRows.size() - 1);
        assertThat(withdrawal.getEntryType()).isEqualTo(CpfLedgerEntryType.LOAN_WITHDRAWAL);
        assertThat(withdrawal.getVpfDebit()).isEqualByComparingTo("30000.00");
        assertThat(withdrawal.getEeShareDebit()).isEqualByComparingTo("20000.00");
        assertThat(withdrawal.getErShareDebit()).isEqualByComparingTo("0.00");
    }

    /** MEDICAL_EMERGENCY's own live-seeded CpfRuleDocument rows (Part 29) - both mandatory. */
    private List<CpfApplicationDocumentSubmission> medicalEmergencyDocuments() {
        return List.of(
                new CpfApplicationDocumentSubmission("Doctor / Specialist Certificate of Major Illness", "s3/test/doctor-cert.pdf", "doctor-cert.pdf"),
                new CpfApplicationDocumentSubmission("Hospital Estimate / Treatment Bills", "s3/test/hospital-bill.pdf", "hospital-bill.pdf"));
    }

    @Test
    void nonRefundableDisbursement_neverCreatesALoan() {
        CpfApplicationResponse applied = applicationService.apply(
                new CpfApplicationRequest("EMP-CPFBRIDGE-1", "MEDICAL_EMERGENCY", new BigDecimal("20000"), new BigDecimal("6000"), null, null,
                        medicalEmergencyDocuments(), null),
                "tester");
        CpfApplicationResponse sanctioned = applicationService.sanction(UUID.fromString(applied.id()),
                new CpfApplicationSanctionRequest(new BigDecimal("20000"), null, new BigDecimal("6000"), null, null, null));

        CpfApplicationResponse disbursed = applicationService.disburse(UUID.fromString(sanctioned.id()));

        assertThat(disbursed.linkedLoanId()).isNull();
        assertThat(loanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())).isEmpty();
    }

    @Test
    void disbursement_retriedAfterSuccess_isIdempotent_oneLoanOneWithdrawal() {
        CpfApplicationResponse sanctioned = applyAndSanctionTemporaryHardship(new BigDecimal("50000"), 24);
        UUID applicationId = UUID.fromString(sanctioned.id());

        CpfApplicationResponse first = applicationService.disburse(applicationId);
        CpfApplicationResponse retry = applicationService.disburse(applicationId);

        assertThat(retry.status()).isEqualTo(CpfApplicationStatus.DISBURSED);
        assertThat(retry.linkedLoanId()).isEqualTo(first.linkedLoanId());
        assertThat(loanApplicationRepository.findByEmployeeIdOrderByCreatedAtDesc(employee.getId())).hasSize(1);

        long withdrawalCount = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).stream()
                .filter(e -> e.getEntryType() == CpfLedgerEntryType.LOAN_WITHDRAWAL).count();
        assertThat(withdrawalCount).isEqualTo(1);
    }

    /**
     * Part 43: forces createLinkedLoan()'s own null-tenure guard to fail AFTER the ledger withdrawal and
     * allocation rows have already been queued in the same @Transactional disburse() call. The guard
     * throws before ever calling loanApplicationRepository.save(...), so within this same Hibernate
     * session there is provably no half-created loan at any point - the remaining atomicity guarantee
     * (the already-queued ledger/allocation writes never reaching a real commit either) is standard
     * Spring @Transactional propagation: a RuntimeException from this REQUIRED-propagation method marks
     * the whole enclosing transaction rollback-only, which is exactly what lets this same test class's
     * other tests keep running against a clean database across test methods.
     */
    @Test
    void disbursement_failureAfterLedgerPosting_neverCreatesAHalfLinkedLoan() {
        CpfApplicationResponse sanctioned = applyAndSanctionTemporaryHardship(new BigDecimal("50000"), 24);
        UUID applicationId = UUID.fromString(sanctioned.id());

        CpfApplication application = applicationRepository.findById(applicationId).orElseThrow();
        application.setTenureMonths(null);
        applicationRepository.saveAndFlush(application);

        assertThatThrownBy(() -> applicationService.disburse(applicationId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("cannot create a repayment schedule");

        assertThat(loanApplicationRepository.findByCpfApplicationId(applicationId)).isEmpty();
    }
}
