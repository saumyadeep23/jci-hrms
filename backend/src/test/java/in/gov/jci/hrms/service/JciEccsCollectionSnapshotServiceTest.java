package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
import in.gov.jci.hrms.dto.JciEccsCollectionDetailResponse;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.dto.JciEccsLoanResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsMemberRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JCIECCS-&gt;Payroll collection snapshot: locking, idempotency (spec section 6: repeated calls for
 * the same payrollRunId return the locked snapshot without recalculation), and the read-only resolver
 * PayrollBatchComputationService folds into totalDeductions - proving it reads the LOCKED snapshot
 * verbatim rather than recomputing, and resolves to zero for a batch with no snapshot yet.
 */
@SpringBootTest
@Transactional
class JciEccsCollectionSnapshotServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsPayrollRecoveryResolverService payrollRecoveryResolverService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;

    private Employee employee;
    private PayrollBatch payrollBatch;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSSN", "JCIECCS Snapshot Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Snapshot Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JES-1", "Snapshot", "Tester", "jes1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        JciEccsMember member = new JciEccsMember(employee.getId(), "JECCS-SN-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        memberRepository.save(member);

        // A far-future, unused (sal_month, sal_year) - payroll_batches has a real uq_payroll_batch_month_year
        // constraint and this shared dev DB already carries other tests' rows for nearer months/years.
        LocalDate disbursementDate = LocalDate.of(2031, 3, 10); // cycle 2031-03, day <= 25
        loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JES-2031-03", 3, 2031, "2030-2031"));
    }

    @Test
    void generateSnapshot_locksThriftAndTermInstallmentOne_forTheMatchingCycle() {
        String payrollRunId = payrollBatch.getId().toString();

        JciEccsCollectionBatchResponse response = snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());

        assertThat(response.cycleCode()).isEqualTo("2031-03");
        // The snapshot legitimately covers every ACTIVE member in this shared dev DB, not just this
        // test's own fixture - filter to this test's own employee rather than asserting total size.
        JciEccsCollectionDetailResponse detail = detailForThisEmployee(response);
        assertThat(detail.thriftAmount()).isEqualByComparingTo("500.00");
        assertThat(detail.termPrincipal()).isEqualTo(10000); // 120000/12 exact
        assertThat(detail.termInterest()).isEqualByComparingTo("1000.00"); // 120000 * 10% / 12
        assertThat(detail.emergencyPrincipal()).isZero();
    }

    @Test
    void generateSnapshot_isIdempotent_repeatedCallReturnsTheSameLockedBatchWithoutRecalculating() {
        String payrollRunId = payrollBatch.getId().toString();

        JciEccsCollectionBatchResponse first = snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
        JciEccsCollectionBatchResponse second = snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.details()).hasSameSizeAs(first.details()); // not duplicated into more rows
        assertThat(second.totalExpectedAmount()).isEqualByComparingTo(first.totalExpectedAmount());
    }

    @Test
    void payrollResolver_readsTheLockedSnapshot_afterItHasBeenRequested() {
        String payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());

        var recovery = payrollRecoveryResolverService.resolve(employee, payrollBatch);

        assertThat(recovery.thrift()).isEqualByComparingTo("500.00");
        assertThat(recovery.termPrincipal()).isEqualByComparingTo("10000");
        assertThat(recovery.termInterest()).isEqualByComparingTo("1000.00");
    }

    @Test
    void payrollResolver_resolvesToZero_whenNoSnapshotHasBeenRequestedYetForThisBatch() {
        var recovery = payrollRecoveryResolverService.resolve(employee, payrollBatch);

        assertThat(recovery).isEqualTo(JciEccsPayrollRecoveryResolverService.RecoveryAmounts.ZERO);
    }

    /** The snapshot batch legitimately includes every ACTIVE member already seeded in this shared dev
     * DB, not just this test's own fixture - isolate this test's own row rather than indexing blindly. */
    private JciEccsCollectionDetailResponse detailForThisEmployee(JciEccsCollectionBatchResponse response) {
        List<JciEccsCollectionDetailResponse> matches = response.details().stream()
                .filter(d -> d.employeeId().equals(employee.getId())).toList();
        assertThat(matches).as("collection detail for employee %s", employee.getId()).hasSize(1);
        return matches.get(0);
    }
}
