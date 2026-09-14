package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfSettlementStatus;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementStatus;
import in.gov.jci.hrms.repository.CpfTrustMemberLedgerEntryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.ExitClearanceRequestRepository;
import in.gov.jci.hrms.repository.TerminalSettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPF Trust Members' List (CpfTrustMemberDirectoryService) - primary identifiers are CPF A/C No and UAN,
 * balances/total payable are derived from the member's latest ledger entry, and settlement status/due-
 * date/lag are always computed via CpfSettlementCalculator rather than stored.
 */
@SpringBootTest
@Transactional
class CpfTrustMemberDirectoryServiceTest {

    @Autowired private CpfTrustMemberDirectoryService memberDirectoryService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private ExitClearanceRequestRepository exitClearanceRequestRepository;
    @Autowired private TerminalSettlementRepository terminalSettlementRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerEntryRepository;

    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = departmentRepository.save(new Department("CPFMBR", "CPF Members Test Dept"));
        designation = designationRepository.save(new Designation("CPF Members Test Officer"));
    }

    private int panSequence = 0;

    private Employee newEmployee(String code, String email, String cpfAcNo, String uanNo, EmployeeStatus status) {
        Employee employee = new Employee(code, "Member", "Tester", email, LocalDate.of(2010, 1, 1), department, designation);
        employee.setCpfAcNo(cpfAcNo);
        employee.setUanNo(uanNo);
        employee.setStatus(status);
        // The short Employee constructor hardcodes panNumber "ABCDE1234F" for every instance, which
        // collides with uq_employees_pan_number_active as soon as a test needs more than one employee.
        employee.setPanNumber("CPFMB" + (panSequence++) + "234F");
        return employeeRepository.save(employee);
    }

    private void addLedgerBalance(Employee employee, BigDecimal ee, BigDecimal er, BigDecimal vpf) {
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, "2020-2021", LocalDate.of(2020, 4, 30),
                CpfLedgerEntryType.OPENING_BALANCE, ee, er, vpf, ee.add(er).add(vpf));
        ledgerEntryRepository.save(entry);
    }

    @Test
    void list_activeMember_isNotApplicableWithNullSeparationFields() {
        newEmployee("EMP-CPFMBR-1", "cpfmbr1@example.com", "CPF-MBR-001", "100000000001", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-001", null, false, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        CpfTrustMemberResponse row = page.getContent().get(0);
        assertThat(row.cpfAcNo()).isEqualTo("CPF-MBR-001");
        assertThat(row.uanNo()).isEqualTo("100000000001");
        assertThat(row.isSeparated()).isFalse();
        assertThat(row.separationDate()).isNull();
        assertThat(row.settlementStatus()).isEqualTo(CpfSettlementStatus.NOT_APPLICABLE);
        assertThat(row.settlementDueDate()).isNull();
        assertThat(row.settlementLagDays()).isNull();
    }

    @Test
    void search_matchesByUanNo() {
        newEmployee("EMP-CPFMBR-2", "cpfmbr2@example.com", "CPF-MBR-002", "100000000002", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "100000000002", null, false, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).employeeCode()).isEqualTo("EMP-CPFMBR-2");
    }

    @Test
    void list_balances_cpfBalanceIsSumOfEeVpfEr_andTotalPayableIncludesAccruedInterest() {
        Employee employee = newEmployee("EMP-CPFMBR-BAL", "cpfmbrbal@example.com", "CPF-MBR-BAL", "100000000099", EmployeeStatus.ACTIVE);
        addLedgerBalance(employee, new BigDecimal("300000.00"), new BigDecimal("120000.00"), new BigDecimal("42450.00"));

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-BAL", null, false, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        CpfTrustMemberResponse row = page.getContent().get(0);
        assertThat(row.eeBalance()).isEqualByComparingTo("300000.00");
        assertThat(row.erBalance()).isEqualByComparingTo("120000.00");
        assertThat(row.vpfBalance()).isEqualByComparingTo("42450.00");
        assertThat(row.cpfBalance()).isEqualByComparingTo("462450.00");
        // Accrued interest depends on whether a statutory rate happens to be notified for the current FY in
        // this environment - not asserted exactly, but totalPayable must never be less than the principal.
        assertThat(row.accruedInterest()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(row.totalPayable()).isEqualByComparingTo(row.cpfBalance().add(row.accruedInterest()));
    }

    @Test
    void list_memberWithNoLedgerActivity_hasZeroBalance() {
        newEmployee("EMP-CPFMBR-NOLEDGER", "cpfmbrnl@example.com", "CPF-MBR-NOLEDGER", "100000000098", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-NOLEDGER", null, false, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).cpfBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void list_separatedMemberWithNoSettlementYet_isPendingOrOverdueNeverSettled() {
        Employee employee = newEmployee("EMP-CPFMBR-3", "cpfmbr3@example.com", "CPF-MBR-003", "100000000003", EmployeeStatus.RESIGNED);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 15), "resignation");
        clearance.setReleaseOrderDate(LocalDate.of(2026, 1, 20));
        exitClearanceRequestRepository.save(clearance);

        Page<CpfTrustMemberResponse> all = memberDirectoryService.list(null, "CPF-MBR-003", null, false, null, null, PageRequest.of(0, 10));
        assertThat(all.getContent()).hasSize(1);
        CpfTrustMemberResponse row = all.getContent().get(0);
        assertThat(row.isSeparated()).isTrue();
        assertThat(row.separationDate()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(row.rawTerminalSettlementStatus()).isNull();
        assertThat(row.settlementDate()).isNull();
        assertThat(row.settlementStatus()).isIn(CpfSettlementStatus.PENDING, CpfSettlementStatus.OVERDUE);
        assertThat(row.settlementDueDate()).isEqualTo(LocalDate.of(2026, 1, 20).plusDays(30));

        Page<CpfTrustMemberResponse> pending = memberDirectoryService.list(null, "CPF-MBR-003", "PENDING", false, null, null, PageRequest.of(0, 10));
        Page<CpfTrustMemberResponse> overdue = memberDirectoryService.list(null, "CPF-MBR-003", "OVERDUE", false, null, null, PageRequest.of(0, 10));
        assertThat(pending.getContent().size() + overdue.getContent().size()).isEqualTo(1);
    }

    @Test
    void list_separatedMemberDisbursed_isSettledNeverOverdue() {
        Employee employee = newEmployee("EMP-CPFMBR-4", "cpfmbr4@example.com", "CPF-MBR-004", "100000000004", EmployeeStatus.RETIRED);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2020, 2, 1), "retirement");
        clearance.setReleaseOrderDate(LocalDate.of(2020, 2, 1));
        exitClearanceRequestRepository.save(clearance);

        TerminalSettlement settlement = draftSettlement(employee);
        settlement.setStatus(TerminalSettlementStatus.DISBURSED);
        terminalSettlementRepository.saveAndFlush(settlement);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-004", null, false, null, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        CpfTrustMemberResponse row = page.getContent().get(0);
        assertThat(row.separationDate()).isEqualTo(LocalDate.of(2020, 2, 1));
        assertThat(row.rawTerminalSettlementStatus()).isEqualTo("DISBURSED");
        assertThat(row.settlementDate()).isNotNull();
        assertThat(row.settlementStatus()).isEqualTo(CpfSettlementStatus.SETTLED);
        assertThat(row.settlementLagLabel()).isEqualTo("Settled");
        assertThat(row.settlementLagDays()).isNull();

        Page<CpfTrustMemberResponse> overdue = memberDirectoryService.list(null, "CPF-MBR-004", "OVERDUE", false, null, null, PageRequest.of(0, 10));
        assertThat(overdue.getContent()).isEmpty();
    }

    @Test
    void list_statusFilter_separatedVsActiveIsGroupedNotLiteral() {
        newEmployee("EMP-CPFMBR-5", "cpfmbr5@example.com", "CPF-MBR-005", "100000000005", EmployeeStatus.ON_PROBATION);
        newEmployee("EMP-CPFMBR-6", "cpfmbr6@example.com", "CPF-MBR-006", "100000000006", EmployeeStatus.TERMINATED);

        Page<CpfTrustMemberResponse> separatedOnly = memberDirectoryService.list("SEPARATED", "CPF-MBR-00", null, false, null, null, PageRequest.of(0, 50));
        assertThat(separatedOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).contains("EMP-CPFMBR-6");
        assertThat(separatedOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).doesNotContain("EMP-CPFMBR-5");

        // ON_PROBATION isn't the literal string "ACTIVE", but it must still count as "Active" (not separated) for this module's grouping.
        Page<CpfTrustMemberResponse> activeOnly = memberDirectoryService.list("ACTIVE", "CPF-MBR-00", null, false, null, null, PageRequest.of(0, 50));
        assertThat(activeOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).contains("EMP-CPFMBR-5");
        assertThat(activeOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).doesNotContain("EMP-CPFMBR-6");
    }

    @Test
    void list_uanMissingFilter_onlyReturnsMembersWithoutUan() {
        newEmployee("EMP-CPFMBR-7", "cpfmbr7@example.com", "CPF-MBR-007", null, EmployeeStatus.ACTIVE);
        newEmployee("EMP-CPFMBR-8", "cpfmbr8@example.com", "CPF-MBR-008", "100000000008", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> missing = memberDirectoryService.list(null, "CPF-MBR-00", null, true, null, null, PageRequest.of(0, 50));

        assertThat(missing.getContent()).extracting(CpfTrustMemberResponse::employeeCode).contains("EMP-CPFMBR-7");
        assertThat(missing.getContent()).extracting(CpfTrustMemberResponse::employeeCode).doesNotContain("EMP-CPFMBR-8");
    }

    @Test
    void list_defaultSort_isCpfAcNoAscending() {
        newEmployee("EMP-CPFMBR-Z", "cpfmbrz@example.com", "CPF-MBR-ZZZ", "100000000091", EmployeeStatus.ACTIVE);
        newEmployee("EMP-CPFMBR-A", "cpfmbra@example.com", "CPF-MBR-AAA", "100000000092", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-", null, false, null, null, PageRequest.of(0, 50));

        var acNos = page.getContent().stream().map(CpfTrustMemberResponse::cpfAcNo).filter(ac -> ac.startsWith("CPF-MBR-AAA") || ac.startsWith("CPF-MBR-ZZZ")).toList();
        int aaaIndex = acNos.indexOf("CPF-MBR-AAA");
        int zzzIndex = acNos.indexOf("CPF-MBR-ZZZ");
        assertThat(aaaIndex).isLessThan(zzzIndex);
    }

    @Test
    void updateUan_persistsNewUanAndReturnsEnrichedRow() {
        Employee employee = newEmployee("EMP-CPFMBR-UAN", "cpfmbruan@example.com", "CPF-MBR-UAN", "100000000010", EmployeeStatus.ACTIVE);

        CpfTrustMemberResponse updated = memberDirectoryService.updateUan(employee.getId(), "999999999999");

        assertThat(updated.uanNo()).isEqualTo("999999999999");
        assertThat(employeeRepository.findById(employee.getId()).orElseThrow().getUanNo()).isEqualTo("999999999999");
    }

    @Test
    void summary_countsActiveSeparatedAndUanMissing() {
        newEmployee("EMP-CPFMBR-S1", "cpfmbrs1@example.com", "CPF-MBR-S1", null, EmployeeStatus.ACTIVE);
        Employee separated = newEmployee("EMP-CPFMBR-S2", "cpfmbrs2@example.com", "CPF-MBR-S2", "100000000020", EmployeeStatus.RESIGNED);
        ExitClearanceRequest clearance = new ExitClearanceRequest(separated, SeparationType.RESIGNATION, LocalDate.of(2020, 1, 1), "resignation");
        clearance.setReleaseOrderDate(LocalDate.of(2020, 1, 1));
        exitClearanceRequestRepository.save(clearance);

        var summary = memberDirectoryService.summary();

        assertThat(summary.totalMembers()).isGreaterThanOrEqualTo(2);
        assertThat(summary.activeAccounts()).isGreaterThanOrEqualTo(1);
        assertThat(summary.separatedMembers()).isGreaterThanOrEqualTo(1);
        assertThat(summary.uanMissing()).isGreaterThanOrEqualTo(1);
        assertThat(summary.overdueSettlement()).isGreaterThanOrEqualTo(1);
    }

    private TerminalSettlement draftSettlement(Employee employee) {
        return new TerminalSettlement(employee, null, SeparationType.SUPERANNUATION, LocalDate.of(2020, 2, 1),
                new BigDecimal("50000.00"), new BigDecimal("10.00"), new BigDecimal("5000.00"),
                10, 0, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("18333.33"), new BigDecimal("4583.33"), new BigDecimal("22916.66"),
                new BigDecimal("50000.00"), false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("77916.66"), BigDecimal.ZERO, new BigDecimal("77916.66"));
    }
}
