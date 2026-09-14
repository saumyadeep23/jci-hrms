package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfDisputeCreateRequest;
import in.gov.jci.hrms.entity.CpfDisputeCategory;
import in.gov.jci.hrms.entity.CpfDisputeStatus;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Self-service Passbook V2 read paths (Parts 4-6/15/16/25/26 of the module spec) - getSummary/getTransactions/
 * getTransactionDetail on {@link CpfTrustPassbookService}, exercised the same way
 * {@link CpfTransactionDisputeService} was: real ledger + payroll fixtures against the dev database, no mocks,
 * since this whole module's guarantee is that every figure traces back to already-posted, authoritative rows.
 */
@SpringBootTest
@Transactional
class CpfSelfServicePassbookServiceTest {

    private static final String FIN_YEAR = "2025-2026";
    private static final int STAT_HEAD_JCPF = 3;
    private static final int STAT_HEAD_PENSION_EPS = 4;

    @Autowired private CpfTrustPassbookService passbookService;
    @Autowired private CpfTransactionDisputeService disputeService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;

    private Employee employeeA;
    private Employee employeeB;
    private CpfTrustMemberLedgerEntry aprilEntry;
    private CpfTrustMemberLedgerEntry mayEntry;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFPB", "CPF Passbook Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF Passbook Test Officer"));
        employeeA = employeeRepository.save(new Employee("EMP-PB-A", "Passbook", "OwnerA", "pbA@example.com",
                LocalDate.of(2015, 1, 1), department, designation));

        Employee pendingEmployeeB = new Employee("EMP-PB-B", "Passbook", "OwnerB", "pbB@example.com",
                LocalDate.of(2015, 1, 1), department, designation);
        pendingEmployeeB.setCpfAcNo("CPF00003");
        pendingEmployeeB.setPanNumber("CBCDE1234F");
        employeeB = employeeRepository.save(pendingEmployeeB);

        seedPayrollMonth(4, 2025, new BigDecimal("1800.00"), new BigDecimal("450.00"));
        aprilEntry = seedLedgerRow(4, 2025, new BigDecimal("2000.00"), new BigDecimal("1800.00"), new BigDecimal("300.00"), BigDecimal.ZERO);

        seedPayrollMonth(5, 2025, new BigDecimal("1800.00"), new BigDecimal("450.00"));
        mayEntry = seedLedgerRow(5, 2025, new BigDecimal("2000.00"), new BigDecimal("1800.00"), new BigDecimal("300.00"), new BigDecimal("4100.00"));
    }

    private void seedPayrollMonth(int month, int year, BigDecimal jcpf, BigDecimal eps) {
        PayrollBatch batch = payrollBatchRepository.save(new PayrollBatch("BATCH-PB-" + year + "-" + month, month, year, FIN_YEAR));
        PayrollMonthlyRecord record = payrollMonthlyRecordRepository.save(
                new PayrollMonthlyRecord(batch, employeeA, employeeA.getEmployeeCode(), month, year, "0001", "OFF", "X", "FULL", 30));
        statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, STAT_HEAD_JCPF, jcpf));
        statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, STAT_HEAD_PENSION_EPS, eps));
    }

    private CpfTrustMemberLedgerEntry seedLedgerRow(int month, int year, BigDecimal ee, BigDecimal er, BigDecimal vpf, BigDecimal priorTotal) {
        LocalDate valueDate = java.time.YearMonth.of(year, month).atEndOfMonth();
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employeeA, FIN_YEAR, valueDate, CpfLedgerEntryType.PAYROLL_MONTHLY,
                ee, er, vpf, ee.add(er).add(vpf).add(priorTotal));
        entry.setEeShareCredit(ee);
        entry.setErShareCredit(er);
        entry.setVpfCredit(vpf);
        entry.setSalMonth(month);
        entry.setSalYear(year);
        return ledgerRepository.save(entry);
    }

    @Test
    void getSummary_bundlesAuthoritativeBalanceAndAdditiveEpsContribution() {
        var summary = passbookService.getSummary(employeeA.getId(), FIN_YEAR);

        assertThat(summary.finYear()).isEqualTo(FIN_YEAR);
        // April's own total (2000+1800+300=4100) plus May's own total (4100) on top of April's running balance.
        assertThat(summary.auditedBalance()).isEqualByComparingTo("8200.00");
        assertThat(summary.contributionSummary().employeeContribution()).isEqualByComparingTo("4000.00");
        assertThat(summary.contributionSummary().employerContribution()).isEqualByComparingTo("3600.00");
        assertThat(summary.contributionSummary().epsContribution()).isEqualByComparingTo("900.00");
        assertThat(summary.contributionSummary().vpfContribution()).isEqualByComparingTo("600.00");
    }

    @Test
    void getTransactions_defaultOrder_isNewestFirst() {
        var page = passbookService.getTransactions(employeeA.getId(), FIN_YEAR, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).id()).isEqualTo(mayEntry.getId());
        assertThat(page.getContent().get(1).id()).isEqualTo(aprilEntry.getId());
    }

    @Test
    void getTransactions_filteredByDateRange_excludesOutOfRangeRows() {
        var page = passbookService.getTransactions(employeeA.getId(), FIN_YEAR, null,
                LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 31), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(mayEntry.getId());
    }

    @Test
    void getTransactions_pagination_splitsResultsAcrossPages() {
        var firstPage = passbookService.getTransactions(employeeA.getId(), FIN_YEAR, null, null, null, PageRequest.of(0, 1));
        var secondPage = passbookService.getTransactions(employeeA.getId(), FIN_YEAR, null, null, null, PageRequest.of(1, 1));

        assertThat(firstPage.getContent()).hasSize(1);
        assertThat(secondPage.getContent()).hasSize(1);
        assertThat(firstPage.getContent().get(0).id()).isNotEqualTo(secondPage.getContent().get(0).id());
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getTransactions_carriesTheOpenDisputeBadgeForItsTransaction() {
        disputeService.raiseDispute(employeeA.getId(),
                new CpfDisputeCreateRequest(aprilEntry.getId(), CpfDisputeCategory.EMPLOYEE_CONTRIBUTION, "This looks off.", null, null));

        var page = passbookService.getTransactions(employeeA.getId(), FIN_YEAR, null, null, null, PageRequest.of(0, 10));

        var aprilRow = page.getContent().stream().filter(r -> r.id().equals(aprilEntry.getId())).findFirst().orElseThrow();
        var mayRow = page.getContent().stream().filter(r -> r.id().equals(mayEntry.getId())).findFirst().orElseThrow();
        assertThat(aprilRow.dispute()).isNotNull();
        assertThat(aprilRow.dispute().status()).isEqualTo(CpfDisputeStatus.OPEN);
        assertThat(mayRow.dispute()).isNull();
    }

    @Test
    void getTransactionDetail_ownTransaction_returnsAuthoritativeFiguresIncludingEps() {
        var detail = passbookService.getTransactionDetail(employeeA.getId(), aprilEntry.getId());

        assertThat(detail.contribution().employeeCpf()).isEqualByComparingTo("2000.00");
        assertThat(detail.contribution().employerCpf()).isEqualByComparingTo("1800.00");
        assertThat(detail.contribution().eps()).isEqualByComparingTo("450.00");
        assertThat(detail.contribution().vpf()).isEqualByComparingTo("300.00");
        assertThat(detail.balance().totalBalance()).isEqualByComparingTo(aprilEntry.getRunningTotalBalance());
        assertThat(detail.source()).isEqualTo("Payroll");
        assertThat(detail.dispute()).isNull();
    }

    @Test
    void getTransactionDetail_anotherEmployeesTransaction_isRejected_idorProtection() {
        assertThatThrownBy(() -> passbookService.getTransactionDetail(employeeB.getId(), aprilEntry.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not belong to this employee");
    }

    @Test
    void getTransactionDetail_nonExistentTransaction_throwsMasterDataNotFound() {
        assertThatThrownBy(() -> passbookService.getTransactionDetail(employeeA.getId(), -1L))
                .isInstanceOf(MasterDataNotFoundException.class);
    }
}
