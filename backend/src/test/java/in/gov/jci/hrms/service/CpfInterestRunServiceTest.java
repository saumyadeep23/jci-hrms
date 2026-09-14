package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.dto.CpfInterestCalculateRequest;
import in.gov.jci.hrms.dto.CpfInterestCalculationPreviewResponse;
import in.gov.jci.hrms.dto.CpfInterestReverseRequest;
import in.gov.jci.hrms.entity.CpfInterestRunScope;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The CPF Interest Management module's Calculate -&gt; Approve/Post -&gt; Reverse -&gt; Recalculate
 * workflow (CpfInterestRunService) - see that class's own javadoc. Runs against the shared dev database
 * (see CpfInterestComputationServiceTest's own javadoc for why); every test here uses
 * scope=SELECTED_MEMBER against a freshly-created test employee so it is unaffected by (and does not
 * affect assertions about) the ~200 real members already carrying CPF ledger activity in this database.
 * FY 2025-2026 is used throughout because the shared DB already has a real notified rate for it (8.25%).
 */
@SpringBootTest
@Transactional
class CpfInterestRunServiceTest {

    private static final String FIN_YEAR = "2025-2026";

    @Autowired private CpfInterestRunService interestRunService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFRUN", "CPF Interest Run Test Dept"));
        designation = designationRepository.save(new Designation("CPF Interest Run Test Officer"));
    }

    private Employee newEmployee(String code, String email, LocalDate joiningDate) {
        Employee employee = employeeRepository.save(new Employee(code, "Run", "Tester", email, joiningDate, department, designation));
        return employee;
    }

    private CpfTrustMemberLedgerEntry seedOpeningBalance(Employee employee, String finYear, LocalDate valueDate, BigDecimal ee, BigDecimal er, BigDecimal vpf) {
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate,
                CpfLedgerEntryType.OPENING_BALANCE, ee, er, vpf, ee.add(er).add(vpf));
        return ledgerRepository.save(entry);
    }

    private CpfInterestCalculationPreviewResponse calculate(Long employeeId, String orderNo) {
        return interestRunService.calculatePreview(
                new CpfInterestCalculateRequest(FIN_YEAR, CpfInterestRunScope.SELECTED_MEMBER, employeeId, orderNo, LocalDate.of(2026, 4, 15)), null);
    }

    // --- Calculate never touches the ledger ---

    @Test
    void calculatePreview_isReadOnly_insertsNoLedgerRow() {
        Employee employee = newEmployee("EMP-RUN-1", "run1@example.com", LocalDate.of(2025, 3, 1));
        seedOpeningBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("50000.00"), new BigDecimal("25000.00"), BigDecimal.ZERO);

        CpfInterestCalculationPreviewResponse preview = calculate(employee.getId(), "INT-ORD/RUN/01");

        assertThat(preview.members()).hasSize(1);
        assertThat(interestRunService.getRun(preview.runId()).status()).isEqualTo(CpfInterestRunStatus.CALCULATED);
        assertThat(ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR)).isEmpty();
    }

    // --- Post is idempotent-safe: a CALCULATED run can only be posted once ---

    @Test
    void postRun_calledTwice_secondCallRejected() {
        Employee employee = newEmployee("EMP-RUN-2", "run2@example.com", LocalDate.of(2025, 3, 1));
        seedOpeningBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        Long runId = calculate(employee.getId(), "INT-ORD/RUN/02").runId();

        CpfAnnualInterestRunResponse posted = interestRunService.postRun(runId, false, null);
        assertThat(posted.status()).isEqualTo(CpfInterestRunStatus.POSTED);

        assertThatThrownBy(() -> interestRunService.postRun(runId, false, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("only a CALCULATED run can be posted");
    }

    // --- Calculate refuses to duplicate an already-active run for the same FY+member ---

    @Test
    void calculatePreview_activeRunAlreadyExistsForSameFyAndMember_isRejected() {
        Employee employee = newEmployee("EMP-RUN-3", "run3@example.com", LocalDate.of(2025, 3, 1));
        seedOpeningBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        calculate(employee.getId(), "INT-ORD/RUN/03");

        assertThatThrownBy(() -> calculate(employee.getId(), "INT-ORD/RUN/03-DUP"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("An active interest run already exists");
    }

    // --- Reverse -> Recalculate -> Post round-trip: original entry preserved, reversal linked, corpus corrected ---

    @Test
    void reverseRun_thenRecalculateAndRepost_preservesOriginalEntryAndAppliesReversal() {
        Employee employee = newEmployee("EMP-RUN-4", "run4@example.com", LocalDate.of(2025, 3, 1));
        seedOpeningBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("120000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        Long firstRunId = calculate(employee.getId(), "INT-ORD/RUN/04").runId();
        CpfAnnualInterestRunResponse posted = interestRunService.postRun(firstRunId, false, null);
        BigDecimal creditedEe = posted.totalInterestCreditedEe();
        assertThat(creditedEe).isGreaterThan(BigDecimal.ZERO);

        List<CpfTrustMemberLedgerEntry> afterPost = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR);
        assertThat(afterPost).hasSize(1);
        CpfTrustMemberLedgerEntry originalCredit = afterPost.get(0);
        assertThat(originalCredit.getEntryType()).isEqualTo(CpfLedgerEntryType.ANNUAL_INTEREST);

        CpfAnnualInterestRunResponse reversed = interestRunService.reverseRun(firstRunId, "Correcting interest order number", null);
        assertThat(reversed.status()).isEqualTo(CpfInterestRunStatus.REVERSED);

        // The original ANNUAL_INTEREST row must survive untouched - accounting history is never deleted.
        List<CpfTrustMemberLedgerEntry> afterReversal = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR);
        assertThat(afterReversal).hasSize(2);
        CpfTrustMemberLedgerEntry stillOriginal = afterReversal.stream().filter(e -> e.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST).findFirst().orElseThrow();
        assertThat(stillOriginal.getEeShareCredit()).isEqualByComparingTo(originalCredit.getEeShareCredit());
        CpfTrustMemberLedgerEntry reversal = afterReversal.stream().filter(e -> e.getEntryType() == CpfLedgerEntryType.ANNUAL_INTEREST_REVERSAL).findFirst().orElseThrow();
        assertThat(reversal.getEeShareDebit()).isEqualByComparingTo(originalCredit.getEeShareCredit());

        // Net effect on the member's running EE balance is back to pre-credit (120000 opening, no contributions this FY).
        CpfTrustMemberLedgerEntry latest = ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId())
                .get(ledgerRepository.findByEmployee_IdOrderByValueDateAscIdAsc(employee.getId()).size() - 1);
        assertThat(latest.getRunningEeBalance()).isEqualByComparingTo("120000.00");

        // Recalculation is allowed once the prior run is REVERSED (the partial-unique-index only guards *active* runs).
        Long secondRunId = calculate(employee.getId(), "INT-ORD/RUN/04-CORRECTED").runId();
        CpfAnnualInterestRunResponse repost = interestRunService.postRun(secondRunId, false, null);
        assertThat(repost.status()).isEqualTo(CpfInterestRunStatus.POSTED);
        assertThat(repost.totalInterestCreditedEe()).isEqualByComparingTo(creditedEe);
    }

    // --- Running-balance cascade: a historical posting inserted before already-existing later rows offsets them, without touching their own credit/debit amounts ---

    @Test
    void postRun_insertedBeforeExistingLaterLedgerRows_cascadesRunningBalanceForward() {
        Employee employee = newEmployee("EMP-RUN-5", "run5@example.com", LocalDate.of(2025, 3, 1));
        seedOpeningBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("100000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        // A row already dated AFTER FY2025-26's own March-31 interest posting date - simulating the
        // already-fully-migrated ledger this module posts historical interest into (Part 18).
        CpfTrustMemberLedgerEntry laterRow = new CpfTrustMemberLedgerEntry(employee, "2026-2027", LocalDate.of(2026, 4, 30),
                CpfLedgerEntryType.PAYROLL_MONTHLY, new BigDecimal("105000.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("105000.00"));
        laterRow.setEeShareCredit(new BigDecimal("5000.00"));
        laterRow = ledgerRepository.save(laterRow);
        BigDecimal laterRowOwnCredit = laterRow.getEeShareCredit();

        Long runId = calculate(employee.getId(), "INT-ORD/RUN/05").runId();
        CpfAnnualInterestRunResponse posted = interestRunService.postRun(runId, false, null);
        BigDecimal creditedEe = posted.totalInterestCreditedEe();
        assertThat(creditedEe).isGreaterThan(BigDecimal.ZERO);

        CpfTrustMemberLedgerEntry laterRowAfterCascade = ledgerRepository.findById(laterRow.getId()).orElseThrow();
        // Its own credit amount must never change (Part 18: "preserve original transaction amounts")...
        assertThat(laterRowAfterCascade.getEeShareCredit()).isEqualByComparingTo(laterRowOwnCredit);
        // ...but its running balance must now include the historically-inserted interest.
        assertThat(laterRowAfterCascade.getRunningEeBalance()).isEqualByComparingTo(new BigDecimal("105000.00").add(creditedEe));
    }
}
