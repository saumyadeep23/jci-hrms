package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.dto.CpfInterestCalculateRequest;
import in.gov.jci.hrms.dto.CpfInterestCalculationPreviewResponse;
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
 * Unit-level coverage of monthEndsFor()'s April-March window, plus real-DB integration tests proving the
 * monthly-product formula and its point-in-time rule for a member whose first ledger row lands mid-year (as
 * a TRANSFER_IN would) - see CpfInterestComputationService's own javadoc for why that rule needs no
 * special-casing beyond the "closing balance as of month-end" lookup itself.
 *
 * <p>The integration tests below use CpfInterestRunService with scope=SELECTED_MEMBER rather than
 * ALL_MEMBERS: this suite runs against the shared dev database (no test-specific datasource is configured -
 * see application.yml), which already carries ~200 real CPF Trust members with real ledger activity: an
 * ALL_MEMBERS run for any FY those members have balances in would sweep them into the same run and make
 * exact-total assertions like "960.00" meaningless (and, before this suite was updated for
 * CpfInterestRunService, it did exactly that - inspecting the live dev DB while updating this file found 114
 * real members with a nonzero balance as of FY2025-26's own opening date, so the previous version of this
 * test asserting {@code totalMembersProcessed == 1} could never actually have passed against this database;
 * a documented PRE-EXISTING/latent issue, not one introduced here). SELECTED_MEMBER scope sidesteps this by
 * construction: it only ever processes the one employee named in the request. FY 2025-2026 is used because
 * the shared DB already has a real notified rate for it (8.25%, see cpf_statutory_interest_rates) - the
 * module's rate is DB-driven per FY (Part 4 of the CPF Interest Management module spec), not caller-supplied,
 * so every expected figure below is computed from that real rate rather than an arbitrary test value.
 */
@SpringBootTest
@Transactional
class CpfInterestComputationServiceTest {

    private static final String FIN_YEAR = "2025-2026";
    private static final BigDecimal REAL_RATE_2025_2026 = new BigDecimal("8.25");

    @Autowired private CpfInterestRunService cpfInterestRunService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFINT", "CPF Interest Test Dept"));
        designation = designationRepository.save(new Designation("CPF Interest Test Officer"));
    }

    private Employee newEmployee(String code, String email) {
        return employeeRepository.save(new Employee(code, "Interest", "Tester", email, LocalDate.of(1985, 1, 1), department, designation));
    }

    private CpfTrustMemberLedgerEntry seedBalance(Employee employee, String finYear, LocalDate valueDate, BigDecimal ee, BigDecimal er, BigDecimal vpf) {
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, finYear, valueDate,
                CpfLedgerEntryType.OPENING_BALANCE, ee, er, vpf, ee.add(er).add(vpf));
        return ledgerRepository.save(entry);
    }

    private CpfAnnualInterestRunResponse calculateAndPost(Long employeeId, String orderNo) {
        CpfInterestCalculationPreviewResponse preview = cpfInterestRunService.calculatePreview(
                new CpfInterestCalculateRequest(FIN_YEAR, CpfInterestRunScope.SELECTED_MEMBER, employeeId, orderNo, LocalDate.of(2026, 4, 15)),
                null);
        return cpfInterestRunService.postRun(preview.runId(), false, null);
    }

    @Test
    void monthEndsFor_returnsTwelveAprilToMarchMonthEnds() {
        List<LocalDate> monthEnds = CpfInterestCalculator.monthEndsFor("2025-2026");

        assertThat(monthEnds).hasSize(12);
        assertThat(monthEnds.get(0)).isEqualTo(LocalDate.of(2025, 4, 30));
        assertThat(monthEnds.get(8)).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(monthEnds.get(11)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void memberInterest_flatBalanceWholeYear_appliesMonthlyProductFormula() {
        Employee employee = newEmployee("EMP-CPFINT-1", "cpfint1@example.com");
        // A single balance established before the FY even starts, so every one of the 12 April-March
        // month-end lookups resolves to this same row - the simplest "monthly product = balance * 12" case.
        seedBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("12000.00"), new BigDecimal("6000.00"), BigDecimal.ZERO);

        CpfAnnualInterestRunResponse response = calculateAndPost(employee.getId(), "INT-ORD/2025-2026/01");

        assertThat(response.status()).isEqualTo(CpfInterestRunStatus.POSTED);
        assertThat(response.totalMembersProcessed()).isEqualTo(1);
        // monthlyProduct = 12000 * 12 = 144000; interest = 144000 * 8.25 / 1200 = 990.00
        assertThat(response.totalInterestCreditedEe()).isEqualByComparingTo("990.00");
        // monthlyProduct = 6000 * 12 = 72000; interest = 72000 * 8.25 / 1200 = 495.00
        assertThat(response.totalInterestCreditedEr()).isEqualByComparingTo("495.00");
        assertThat(response.totalInterestCreditedVpf()).isEqualByComparingTo("0.00");

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR);
        assertThat(entries).hasSize(1);
        CpfTrustMemberLedgerEntry interestEntry = entries.get(0);
        assertThat(interestEntry.getEntryType()).isEqualTo(CpfLedgerEntryType.ANNUAL_INTEREST);
        assertThat(interestEntry.getValueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(interestEntry.getInterestCredit()).isEqualByComparingTo("1485.00");
        assertThat(interestEntry.getRunningEeBalance()).isEqualByComparingTo("12990.00");
        assertThat(interestEntry.getRunningErBalance()).isEqualByComparingTo("6495.00");
    }

    @Test
    void memberInterest_joinsMidYearViaTransfer_onlyAccruesFromThatMonthOnward() {
        Employee employee = newEmployee("EMP-CPFINT-2", "cpfint2@example.com");
        // Joining date matches the simulated TRANSFER_IN below - otherwise this employee's default
        // (1985) joining date would predate the FY's opening-balance date with no ledger row to confirm
        // it, correctly tripping the DATA_REVIEW_REQUIRED check (Part 9/31) meant for a genuinely
        // unconfirmed pre-migration balance, which is not what this test is exercising.
        employee.setDateOfJoining(LocalDate.of(2025, 10, 1));
        employeeRepository.save(employee);
        // First-ever ledger row lands in October (simulating a TRANSFER_IN mid-FY) - April through
        // September must contribute zero to the monthly product, per the point-in-time rule.
        seedBalance(employee, FIN_YEAR, LocalDate.of(2025, 10, 31), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        CpfAnnualInterestRunResponse response = calculateAndPost(employee.getId(), "INT-ORD/2025-2026/02");

        // Interest is credited on each month's OPENING balance (the closing balance carried in from the
        // PRECEDING month), not the closing balance including that month's own contribution - so the
        // October contribution itself first counts as an opening balance in November, not October.
        // Nov, Dec, Jan, Feb, Mar openings (each = October's 10000 closing balance) = 5 months at 10000
        // each = 50000 monthly product. interest = 50000 * 8.25 / 1200 = 343.75
        assertThat(response.totalInterestCreditedEe()).isEqualByComparingTo("344"); // aggregate rounds HALF_UP to the nearest rupee
        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR);
        CpfTrustMemberLedgerEntry interestEntry = entries.get(entries.size() - 1);
        assertThat(interestEntry.getEeShareCredit()).isEqualByComparingTo("343.75");
    }

    @Test
    void calculatePreview_financialYearOutsideMigratedLedgerSpan_isRejected() {
        Employee employee = newEmployee("EMP-CPFINT-3", "cpfint3@example.com");
        seedBalance(employee, "2029-2030", LocalDate.of(2030, 1, 1), new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        // FY2030-2031 has no notified rate and sits well beyond the ledger's own latest transaction date -
        // the module must refuse to fabricate a calculation for it rather than silently posting a
        // zero-member run (Part 2/9/31 of the module spec: only show/calculate FYs with a valid basis).
        assertThatThrownBy(() -> cpfInterestRunService.calculatePreview(
                new CpfInterestCalculateRequest("2030-2031", CpfInterestRunScope.SELECTED_MEMBER, employee.getId(),
                        "INT-ORD/2030-2031/01", LocalDate.of(2031, 4, 15)),
                null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("outside the calculable range");
    }
}
