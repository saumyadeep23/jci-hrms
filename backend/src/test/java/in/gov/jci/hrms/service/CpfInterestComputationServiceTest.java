package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfAnnualInterestRunResponse;
import in.gov.jci.hrms.entity.CpfInterestRunStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
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

/**
 * Unit-level coverage of monthEndsFor()'s April-March window, plus a real-DB integration test (matching
 * this task's other new tests) proving computeAnnualInterest()'s monthly-product formula and its
 * point-in-time rule for a member whose first ledger row lands mid-year (as a TRANSFER_IN would) - see
 * CpfInterestComputationService's own javadoc for why that rule needs no special-casing beyond the
 * "closing balance as of month-end" lookup itself.
 */
@SpringBootTest
@Transactional
class CpfInterestComputationServiceTest {

    @Autowired private CpfInterestComputationService cpfInterestComputationService;
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

    @Test
    void monthEndsFor_returnsTwelveAprilToMarchMonthEnds() {
        List<LocalDate> monthEnds = CpfInterestComputationService.monthEndsFor("2025-2026");

        assertThat(monthEnds).hasSize(12);
        assertThat(monthEnds.get(0)).isEqualTo(LocalDate.of(2025, 4, 30));
        assertThat(monthEnds.get(8)).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(monthEnds.get(11)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void computeAnnualInterest_flatBalanceWholeYear_appliesMonthlyProductFormula() {
        Employee employee = newEmployee("EMP-CPFINT-1", "cpfint1@example.com");
        // A single balance established before the FY even starts, so every one of the 12 April-March
        // month-end lookups resolves to this same row - the simplest "monthly product = balance * 12" case.
        seedBalance(employee, "2024-2025", LocalDate.of(2025, 3, 1), new BigDecimal("12000.00"), new BigDecimal("6000.00"), BigDecimal.ZERO);

        CpfAnnualInterestRunResponse response = cpfInterestComputationService.computeAnnualInterest("2025-2026", new BigDecimal("8.00"),
                "INT-ORD/2025-2026/01", LocalDate.of(2026, 4, 15), null);

        assertThat(response.status()).isEqualTo(CpfInterestRunStatus.POSTED);
        assertThat(response.totalMembersProcessed()).isEqualTo(1);
        // monthlyProduct = 12000 * 12 = 144000; interest = 144000 * 8 / 1200 = 960.00
        assertThat(response.totalInterestCreditedEe()).isEqualByComparingTo("960.00");
        // monthlyProduct = 6000 * 12 = 72000; interest = 72000 * 8 / 1200 = 480.00
        assertThat(response.totalInterestCreditedEr()).isEqualByComparingTo("480.00");
        assertThat(response.totalInterestCreditedVpf()).isEqualByComparingTo("0.00");

        List<CpfTrustMemberLedgerEntry> entries = ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), "2025-2026");
        assertThat(entries).hasSize(1);
        CpfTrustMemberLedgerEntry interestEntry = entries.get(0);
        assertThat(interestEntry.getEntryType()).isEqualTo(CpfLedgerEntryType.ANNUAL_INTEREST);
        assertThat(interestEntry.getValueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(interestEntry.getInterestCredit()).isEqualByComparingTo("1440.00");
        assertThat(interestEntry.getRunningEeBalance()).isEqualByComparingTo("12960.00");
        assertThat(interestEntry.getRunningErBalance()).isEqualByComparingTo("6480.00");
    }

    @Test
    void computeAnnualInterest_memberJoinsMidYearViaTransfer_onlyAccruesFromThatMonthOnward() {
        Employee employee = newEmployee("EMP-CPFINT-2", "cpfint2@example.com");
        // First-ever ledger row lands in October (simulating a TRANSFER_IN mid-FY) - April through
        // September must contribute zero to the monthly product, per the point-in-time rule.
        seedBalance(employee, "2025-2026", LocalDate.of(2025, 10, 31), new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        CpfAnnualInterestRunResponse response = cpfInterestComputationService.computeAnnualInterest("2025-2026", new BigDecimal("6.00"),
                "INT-ORD/2025-2026/02", LocalDate.of(2026, 4, 15), null);

        // Oct, Nov, Dec, Jan, Feb, Mar = 6 months at 10000 each = 60000 monthly product.
        // interest = 60000 * 6 / 1200 = 300.00
        assertThat(response.totalInterestCreditedEe()).isEqualByComparingTo("300.00");
    }

    @Test
    void computeAnnualInterest_noLedgerActivity_postsZeroMemberRun() {
        CpfAnnualInterestRunResponse response = cpfInterestComputationService.computeAnnualInterest("2030-2031", new BigDecimal("8.00"),
                "INT-ORD/2030-2031/01", LocalDate.of(2031, 4, 15), null);

        assertThat(response.status()).isEqualTo(CpfInterestRunStatus.POSTED);
        assertThat(response.totalMembersProcessed()).isEqualTo(0);
        assertThat(response.totalInterestCreditedEe()).isEqualByComparingTo("0.00");
    }
}
