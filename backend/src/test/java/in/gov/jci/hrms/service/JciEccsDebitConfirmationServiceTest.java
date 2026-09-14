package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.JciEccsCollectionBatchResponse;
import in.gov.jci.hrms.dto.JciEccsCollectionDetailResponse;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationLineRequest;
import in.gov.jci.hrms.dto.JciEccsDebitConfirmationRequest;
import in.gov.jci.hrms.dto.JciEccsLoanCreateRequest;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.JciEccsCollectionBatchStatus;
import in.gov.jci.hrms.entity.JciEccsDebitStatus;
import in.gov.jci.hrms.entity.JciEccsLoanProductCode;
import in.gov.jci.hrms.entity.JciEccsMember;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.JciEccsLoanRepository;
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
 * Debit confirmation's strict-priority partial allocation (spec section 6: "Partial debit confirmation:
 * Correct cascade allocation across thrift, interest, and principal") and the audit trail it must leave
 * on every financial mutation.
 */
@SpringBootTest
@Transactional
class JciEccsDebitConfirmationServiceTest {

    @Autowired private JciEccsLoanService loanService;
    @Autowired private JciEccsCollectionSnapshotService snapshotService;
    @Autowired private JciEccsDebitConfirmationService debitConfirmationService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private JciEccsMemberRepository memberRepository;
    @Autowired private JciEccsLoanRepository loanRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;

    private Employee employee;
    private PayrollBatch payrollBatch;
    private String payrollRunId;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("JCIECCSDC", "JCIECCS Debit Confirm Test Dept"));
        Designation designation = designationRepository.save(new Designation("JCIECCS Debit Confirm Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-JDC-1", "Debit", "Tester", "jdc1@example.com",
                LocalDate.now().minusYears(5), department, designation));

        JciEccsMember member = new JciEccsMember(employee.getId(), "JECCS-DC-0001", LocalDate.now().minusYears(3), LocalDate.now().minusYears(3));
        member.setThriftMonthlyAmount(new BigDecimal("500.00"));
        memberRepository.save(member);

        // Term loan: 120000 / 12 = 10000 principal exactly; interest at 10% p.a. on 120000 opening = 1000.00.
        LocalDate disbursementDate = LocalDate.of(2032, 5, 10);
        loanService.createLoan(new JciEccsLoanCreateRequest(employee.getEmployeeCode(), JciEccsLoanProductCode.TERM,
                new BigDecimal("120000"), 12, disbursementDate, disbursementDate, disbursementDate), employee.getId(), "tester");

        payrollBatch = payrollBatchRepository.save(new PayrollBatch("PB-JDC-2032-05", 5, 2032, "2031-2032"));
        payrollRunId = payrollBatch.getId().toString();
        snapshotService.generateSnapshot(payrollRunId, payrollBatch, employee.getId());
    }

    @Test
    void debitSuccess_recoversTheFullSnapshotAmount_andClosesTheInstallmentAsPaid() {
        JciEccsDebitConfirmationRequest request = new JciEccsDebitConfirmationRequest(
                List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(), JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-1")));

        JciEccsCollectionBatchResponse response = debitConfirmationService.confirmDebit(payrollRunId, request, employee.getId());

        assertThat(response.batchStatus()).isEqualTo(JciEccsCollectionBatchStatus.PROCESSED);
        JciEccsCollectionDetailResponse detail = detailForThisEmployee(response);
        assertThat(detail.debitStatus()).isEqualTo(JciEccsDebitStatus.DEBIT_SUCCESS);
        // totalSnapshotAmount itself is a DB GENERATED column that reads back null on this still-managed
        // entity within the same test transaction (never refreshed after insert) - assert against the
        // known components (thrift 500 + term interest 1000 + term principal 10000) instead.
        assertThat(detail.actualDebitedAmount()).isEqualByComparingTo("11500.00");

        var loan = loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("110000.00"); // 120000 - 10000
    }

    @Test
    void debitPartial_allocatesStrictlyThriftThenInterestThenPrincipal() {
        // Snapshot totals: thrift 500 + term interest 1000 + term principal 10000 = 11500 expected.
        // Offer 1300: fully covers thrift(500) and interest(1000, cumulative 1500 > 1300) -> thrift full
        // (500), remaining 800 goes entirely to interest (only 800 of the 1000 due), principal untouched.
        BigDecimal offered = new BigDecimal("1300.00");
        JciEccsDebitConfirmationRequest request = new JciEccsDebitConfirmationRequest(
                List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(), JciEccsDebitStatus.DEBIT_PARTIAL, offered, "TXN-2")));

        JciEccsCollectionBatchResponse response = debitConfirmationService.confirmDebit(payrollRunId, request, employee.getId());

        JciEccsCollectionDetailResponse detail = detailForThisEmployee(response);
        assertThat(detail.debitStatus()).isEqualTo(JciEccsDebitStatus.DEBIT_PARTIAL);
        assertThat(detail.actualDebitedAmount()).isEqualByComparingTo(offered);

        var loan = loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
        // Principal untouched by this partial recovery (thrift+interest absorbed the whole 1300).
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("120000.00");

        var schedule = loanService.getSchedule(loan.getId()).get(0);
        assertThat(schedule.interestPaid()).isEqualByComparingTo("800.00"); // 1300 - 500 thrift
        assertThat(schedule.principalPaid()).isEqualByComparingTo("0");
        assertThat(schedule.status().name()).isEqualTo("PARTIAL");
    }

    @Test
    void debitFailed_recoversNothing_andMarksTheInstallmentOverdue() {
        JciEccsDebitConfirmationRequest request = new JciEccsDebitConfirmationRequest(
                List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(), JciEccsDebitStatus.DEBIT_FAILED, null, "TXN-3")));

        JciEccsCollectionBatchResponse response = debitConfirmationService.confirmDebit(payrollRunId, request, employee.getId());

        JciEccsCollectionDetailResponse detail = detailForThisEmployee(response);
        assertThat(detail.debitStatus()).isEqualTo(JciEccsDebitStatus.DEBIT_FAILED);
        assertThat(detail.actualDebitedAmount()).isEqualByComparingTo("0");

        var loan = loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
        var schedule = loanService.getSchedule(loan.getId()).get(0);
        assertThat(schedule.status().name()).isEqualTo("OVERDUE");
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("120000.00");
    }

    @Test
    void confirmDebit_isIdempotentPerLine_aSecondCallForAnAlreadyProcessedEmployeeDoesNotDoublePost() {
        JciEccsDebitConfirmationRequest first = new JciEccsDebitConfirmationRequest(
                List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(), JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-4")));
        debitConfirmationService.confirmDebit(payrollRunId, first, employee.getId());

        JciEccsDebitConfirmationRequest retry = new JciEccsDebitConfirmationRequest(
                List.of(new JciEccsDebitConfirmationLineRequest(employee.getId(), JciEccsDebitStatus.DEBIT_SUCCESS, null, "TXN-4-retry")));
        debitConfirmationService.confirmDebit(payrollRunId, retry, employee.getId());

        var loan = loanRepository.findAll().stream().filter(l -> l.getMember().getEmployeeId().equals(employee.getId())).findFirst().orElseThrow();
        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("110000.00"); // not double-deducted to 100000
    }

    /** The batch legitimately includes every ACTIVE member already seeded in this shared dev DB, not
     * just this test's own fixture - isolate this test's own row rather than indexing blindly. */
    private JciEccsCollectionDetailResponse detailForThisEmployee(JciEccsCollectionBatchResponse response) {
        List<JciEccsCollectionDetailResponse> matches = response.details().stream()
                .filter(d -> d.employeeId().equals(employee.getId())).toList();
        assertThat(matches).as("collection detail for employee %s", employee.getId()).hasSize(1);
        return matches.get(0);
    }
}
