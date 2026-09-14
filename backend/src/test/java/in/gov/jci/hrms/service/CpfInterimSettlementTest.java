package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfPassbookResponseDto;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfStatutoryInterestRate;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.CpfStatutoryInterestRateRepository;
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
 * Mid-year interest crystallization on exit - CpfInterestComputationService.crystallizeInterimInterest() -
 * and its effect on CpfTrustPassbookService's shadow-accrual read afterward.
 */
@SpringBootTest
@Transactional
class CpfInterimSettlementTest {

    @Autowired private CpfInterestComputationService cpfInterestComputationService;
    @Autowired private CpfTrustPassbookService cpfTrustPassbookService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private CpfStatutoryInterestRateRepository rateRepository;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFSETL", "CPF Interim Settlement Test Dept"));
        designation = designationRepository.save(new Designation("CPF Interim Settlement Test Officer"));
        // FY 2041-2042 (the settlement's own FY) is deliberately left unnotified - only the preceding FY
        // 2040-2041 is, forcing the Para 60(2) fallback for every test in this class. This range is picked
        // far from the one real notified row the live dev DB already carries (FY 2026-2027 - see V77's own
        // header comment), so it can never collide with cpf_statutory_interest_rates' UNIQUE(fin_year).
        rateRepository.save(new CpfStatutoryInterestRate("2040-2041", new BigDecimal("8.25"), new BigDecimal("1.00"),
                "INT-ORD/2040-2041/01", LocalDate.of(2041, 4, 1)));
    }

    private Employee newEmployee(String code, String email) {
        return employeeRepository.save(new Employee(code, "Settlement", "Tester", email, LocalDate.of(1985, 1, 1), department, designation));
    }

    private void seedOpeningBalance(Employee employee, BigDecimal ee, BigDecimal er, BigDecimal vpf) {
        ledgerRepository.save(new CpfTrustMemberLedgerEntry(employee, "2041-2042", LocalDate.of(2041, 3, 1),
                CpfLedgerEntryType.OPENING_BALANCE, ee, er, vpf, ee.add(er).add(vpf)));
    }

    @Test
    void crystallizeInterimInterest_midYearResignation_postsProvisionalInterimEntry() {
        Employee employee = newEmployee("EMP-CPFSETL-1", "cpfsetl1@example.com");
        seedOpeningBalance(employee, new BigDecimal("12000.00"), new BigDecimal("6000.00"), BigDecimal.ZERO);

        CpfTrustMemberLedgerEntry posted = cpfInterestComputationService.crystallizeInterimInterest(
                employee.getId(), LocalDate.of(2041, 9, 30), "RESIGNATION", null);

        assertThat(posted.getEntryType()).isEqualTo(CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST);
        assertThat(posted.isProvisionalRate()).isTrue();
        assertThat(posted.getRateApplied()).isEqualByComparingTo("8.25");
        assertThat(posted.getRateSourceFinYear()).isEqualTo("2040-2041");
        assertThat(posted.getFinYear()).isEqualTo("2041-2042");
        // Apr-Sep 2041 (6 elapsed months) at a flat 12000/6000 balance: (72000*8.25/1200)=495.00,
        // (36000*8.25/1200)=247.50. Per-component amounts are never individually rounded to whole rupees
        // (see CpfInterestCalculator's own javadoc) - only the aggregate total_credit/interest_credit is;
        // eeShareCredit/erShareCredit/runningTotalBalance keep full (2-decimal) precision.
        assertThat(posted.getEeShareCredit()).isEqualByComparingTo("495.00");
        assertThat(posted.getErShareCredit()).isEqualByComparingTo("247.50");
        assertThat(posted.getRunningTotalBalance()).isEqualByComparingTo("18742.50");
        // The aggregate (495.00 + 247.50 = 742.50) IS rounded, HALF_UP, to the nearest whole rupee: 743.
        assertThat(posted.getInterestCredit()).isEqualByComparingTo("743");
        assertThat(posted.getTotalCredit()).isEqualByComparingTo("743");
    }

    @Test
    void crystallizeInterimInterest_priorInterestAlreadyPostedThisFy_rejects() {
        Employee employee = newEmployee("EMP-CPFSETL-2", "cpfsetl2@example.com");
        seedOpeningBalance(employee, new BigDecimal("10000.00"), BigDecimal.ZERO, BigDecimal.ZERO);
        cpfInterestComputationService.crystallizeInterimInterest(employee.getId(), LocalDate.of(2041, 6, 30), "TRANSFER_OUT", null);

        assertThatThrownBy(() -> cpfInterestComputationService.crystallizeInterimInterest(
                employee.getId(), LocalDate.of(2041, 9, 30), "RESIGNATION", null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already been posted");
    }

    @Test
    void passbookRead_afterInterimSettlement_showsUpdatedBalanceAndNoRemainingShadowAccrual() {
        Employee employee = newEmployee("EMP-CPFSETL-3", "cpfsetl3@example.com");
        seedOpeningBalance(employee, new BigDecimal("12000.00"), new BigDecimal("6000.00"), BigDecimal.ZERO);
        cpfInterestComputationService.crystallizeInterimInterest(employee.getId(), LocalDate.of(2041, 9, 30), "DEATH", null);

        CpfPassbookResponseDto passbook = cpfTrustPassbookService.getPassbook(employee.getId(), "2041-2042");

        assertThat(passbook.isProvisionalRate()).isTrue();
        assertThat(passbook.rateSourceFinYear()).isEqualTo("2040-2041");
        assertThat(passbook.ledgerBalance()).isEqualByComparingTo("18742.50");
        // Interest for this FY is already posted (the INTERIM_SETTLEMENT_INTEREST row itself), so the
        // dynamic shadow-accrual projection must not double-count it.
        assertThat(passbook.accruedInterestFytd()).isEqualByComparingTo("0.00");
        assertThat(passbook.effectiveTotalCorpus()).isEqualByComparingTo("18742.50");

        List<CpfLedgerEntryType> types = passbook.entries().stream().map(e -> e.entryType()).toList();
        assertThat(types).containsExactly(CpfLedgerEntryType.OPENING_BALANCE, CpfLedgerEntryType.INTERIM_SETTLEMENT_INTEREST);
    }
}
