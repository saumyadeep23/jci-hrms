package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfDisputeCreateRequest;
import in.gov.jci.hrms.dto.CpfDisputeResponse;
import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.ConcurrencyConflictException;
import in.gov.jci.hrms.exception.DuplicateDisputeException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.AuditLogRepository;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The CPF Transaction Dispute lifecycle (Parts 9-13/28-30 of the Passbook V2 module spec) - especially its
 * two non-negotiable safety properties: a dispute action NEVER alters the referenced
 * {@link CpfTrustMemberLedgerEntry} (Part 2/13), and an employee can never act on another employee's
 * transaction or dispute (Part 21 IDOR protection).
 */
@SpringBootTest
@Transactional
class CpfTransactionDisputeServiceTest {

    @Autowired private CpfTransactionDisputeService disputeService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @PersistenceContext private EntityManager entityManager;

    private Employee employeeA;
    private Employee employeeB;
    private CpfTrustMemberLedgerEntry transactionOfA;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFDSP", "CPF Dispute Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Dispute Test Officer"));
        employeeA = employeeRepository.save(new Employee("EMP-DSP-A", "Dispute", "OwnerA", "dspA@example.com",
                LocalDate.of(2015, 1, 1), department, designation));
        Employee pendingEmployeeB = new Employee("EMP-DSP-B", "Dispute", "OwnerB", "dspB@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        // The 7-arg convenience constructor hardcodes cpfAcNo="CPF00001"/panNumber="ABCDE1234F" (fine for
        // single-employee fixtures elsewhere) - this test seeds two employees, so employeeB needs distinct
        // values to satisfy uq_employees_cpf_ac_no_active / uq_employees_pan_number_active.
        pendingEmployeeB.setCpfAcNo("CPF00002");
        pendingEmployeeB.setPanNumber("BBCDE1234F");
        employeeB = employeeRepository.save(pendingEmployeeB);

        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employeeA, "2025-2026", LocalDate.of(2025, 4, 30),
                CpfLedgerEntryType.PAYROLL_MONTHLY, new BigDecimal("12000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO,
                new BigDecimal("22000.00"));
        entry.setEeShareCredit(new BigDecimal("2000.00"));
        entry.setErShareCredit(new BigDecimal("1800.00"));
        transactionOfA = ledgerRepository.save(entry);
    }

    private CpfDisputeCreateRequest requestFor(Long ledgerTransactionId, CpfDisputeCategory category) {
        return new CpfDisputeCreateRequest(ledgerTransactionId, category, "The employee contribution looks wrong this month.", null, null);
    }

    @Test
    void raiseDispute_ownTransaction_succeedsAndAuditsCreation() {
        CpfDisputeResponse response = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION));

        assertThat(response.status()).isEqualTo(CpfDisputeStatus.OPEN);
        assertThat(response.disputeNumber()).startsWith("CPF-DSP-");

        var auditRows = auditLogRepository.findByEntityNameAndEntityIdOrderByCreatedAtAsc("CpfTransactionDispute", response.id());
        assertThat(auditRows).isNotEmpty();
        assertThat(auditRows.get(0).getAction().name()).isEqualTo("CREATE");
    }

    @Test
    void raiseDispute_anotherEmployeesTransaction_isRejected_idorProtection() {
        assertThatThrownBy(() -> disputeService.raiseDispute(employeeB.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not belong to this employee");
    }

    @Test
    void getMyDispute_anotherEmployeesDispute_isRejected_idorProtection() {
        CpfDisputeResponse own = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION));

        assertThatThrownBy(() -> disputeService.getMyDispute(employeeB.getId(), own.id()))
                .isInstanceOf(MasterDataNotFoundException.class);
    }

    @Test
    void raiseDispute_duplicateActiveForSameTransactionAndCategory_isRejected() {
        disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION));

        assertThatThrownBy(() -> disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION)))
                .isInstanceOf(DuplicateDisputeException.class);
    }

    @Test
    void raiseDispute_sameTransactionDifferentCategory_isAllowed() {
        disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION));
        CpfDisputeResponse second = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYER_CONTRIBUTION));
        assertThat(second.status()).isEqualTo(CpfDisputeStatus.OPEN);
    }

    @Test
    void fullLifecycle_openReviewClarificationRespondResolve_neverTouchesLedger() {
        BigDecimal originalEeCredit = transactionOfA.getEeShareCredit();
        BigDecimal originalRunningTotal = transactionOfA.getRunningTotalBalance();

        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION));
        var afterStart = disputeService.startReview(created.id(), created.version(), null);
        assertThat(afterStart.status()).isEqualTo(CpfDisputeStatus.UNDER_REVIEW);

        var afterClarification = disputeService.requestClarification(created.id(), "Please share your payslip for April 2025.", afterStart.version(), null);
        assertThat(afterClarification.status()).isEqualTo(CpfDisputeStatus.CLARIFICATION_REQUIRED);

        var afterResponse = disputeService.respondToClarification(employeeA.getId(), created.id(), "Attached in the portal already.");
        assertThat(afterResponse.status()).isEqualTo(CpfDisputeStatus.UNDER_REVIEW);

        var resolved = disputeService.resolve(created.id(), "Verified against payroll - figures are correct, no correction needed.", afterResponse.version(), null);
        assertThat(resolved.status()).isEqualTo(CpfDisputeStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();

        // The ledger transaction itself must be byte-for-byte unchanged after the entire workflow.
        CpfTrustMemberLedgerEntry reloaded = ledgerRepository.findById(transactionOfA.getId()).orElseThrow();
        assertThat(reloaded.getEeShareCredit()).isEqualByComparingTo(originalEeCredit);
        assertThat(reloaded.getRunningTotalBalance()).isEqualByComparingTo(originalRunningTotal);
    }

    @Test
    void reject_fromUnderReview_succeeds() {
        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.BALANCE));
        var afterStart = disputeService.startReview(created.id(), created.version(), null);
        var rejected = disputeService.reject(created.id(), "No discrepancy found after review.", afterStart.version(), null);
        assertThat(rejected.status()).isEqualTo(CpfDisputeStatus.REJECTED);
        assertThat(rejected.rejectedAt()).isNotNull();
    }

    @Test
    void resolve_fromOpen_invalidTransition_isRejected() {
        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.OTHER));
        assertThatThrownBy(() -> disputeService.resolve(created.id(), "trying to skip review", created.version(), null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only a UNDER_REVIEW dispute can be resolved");
    }

    @Test
    void withdraw_onlyFromOpen_isRejectedOnceUnderReview() {
        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.INTEREST));
        disputeService.startReview(created.id(), created.version(), null);

        assertThatThrownBy(() -> disputeService.withdraw(employeeA.getId(), created.id()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only a OPEN dispute can be withdrawn");
    }

    @Test
    void withdraw_fromOpen_succeeds() {
        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.WITHDRAWAL));
        var withdrawn = disputeService.withdraw(employeeA.getId(), created.id());
        assertThat(withdrawn.status()).isEqualTo(CpfDisputeStatus.WITHDRAWN);
    }

    @Test
    void afterWithdrawal_theSameTransactionAndCategoryCanBeDisputedAgain() {
        CpfDisputeResponse first = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.VPF_CONTRIBUTION));
        disputeService.withdraw(employeeA.getId(), first.id());

        CpfDisputeResponse second = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.VPF_CONTRIBUTION));
        assertThat(second.status()).isEqualTo(CpfDisputeStatus.OPEN);
        assertThat(second.id()).isNotEqualTo(first.id());
    }

    @Test
    void optimisticLocking_staleExpectedVersion_isRejectedWithConcurrencyConflict() {
        CpfDisputeResponse created = disputeService.raiseDispute(employeeA.getId(), requestFor(transactionOfA.getId(), CpfDisputeCategory.LOAN_REPAYMENT));
        Long staleVersion = created.version();

        // Reviewer A starts review, bumping the version. An explicit flush is required here: within this
        // single @Transactional test method nothing has actually committed, and Hibernate only physically
        // increments an in-memory @Version field when the UPDATE is flushed - not merely on setStatus() -
        // so without this the second call below would still see the pre-update version and the race this
        // test is simulating (two separate REST calls/transactions) would go undetected.
        disputeService.startReview(created.id(), staleVersion, null);
        entityManager.flush();

        // Reviewer B, still holding the pre-review version, tries to act - must be rejected, not silently applied.
        assertThatThrownBy(() -> disputeService.requestClarification(created.id(), "late clarification request", staleVersion, null))
                .isInstanceOf(ConcurrencyConflictException.class);
    }
}
