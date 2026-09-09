package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.CpfTrustMemberResponse;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.EmployeeStatus;
import in.gov.jci.hrms.entity.ExitClearanceRequest;
import in.gov.jci.hrms.entity.SeparationType;
import in.gov.jci.hrms.entity.TerminalSettlement;
import in.gov.jci.hrms.entity.TerminalSettlementStatus;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CPF Trust Members' List (CpfTrustMemberDirectoryService) - primary identifiers are CPF A/C No and UAN,
 * and separated members whose CPF settlement is still outstanding (or was disbursed well after their
 * separation date) are exactly what pendingSettlement=true and settlementLagDays exist to surface.
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

    @Test
    void list_activeMember_hasNullSeparationAndSettlementFields() {
        newEmployee("EMP-CPFMBR-1", "cpfmbr1@example.com", "CPF-MBR-001", "100000000001", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-001", false, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        CpfTrustMemberResponse row = page.getContent().get(0);
        assertThat(row.cpfAcNo()).isEqualTo("CPF-MBR-001");
        assertThat(row.uanNo()).isEqualTo("100000000001");
        assertThat(row.separationDate()).isNull();
        assertThat(row.cpfSettlementStatus()).isNull();
        assertThat(row.settlementLagDays()).isNull();
    }

    @Test
    void search_matchesByUanNo() {
        newEmployee("EMP-CPFMBR-2", "cpfmbr2@example.com", "CPF-MBR-002", "100000000002", EmployeeStatus.ACTIVE);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "100000000002", false, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).employeeCode()).isEqualTo("EMP-CPFMBR-2");
    }

    @Test
    void list_separatedMemberWithNoSettlementYet_showsSeparationDateAndIsPendingSettlement() {
        Employee employee = newEmployee("EMP-CPFMBR-3", "cpfmbr3@example.com", "CPF-MBR-003", "100000000003", EmployeeStatus.RESIGNED);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.RESIGNATION, LocalDate.of(2026, 1, 15), "resignation");
        clearance.setReleaseOrderDate(LocalDate.of(2026, 1, 20));
        exitClearanceRequestRepository.save(clearance);

        Page<CpfTrustMemberResponse> all = memberDirectoryService.list(null, "CPF-MBR-003", false, PageRequest.of(0, 10));
        assertThat(all.getContent()).hasSize(1);
        CpfTrustMemberResponse row = all.getContent().get(0);
        assertThat(row.separationDate()).isEqualTo(LocalDate.of(2026, 1, 20));
        assertThat(row.cpfSettlementStatus()).isNull();
        assertThat(row.cpfSettlementDate()).isNull();
        assertThat(row.settlementLagDays()).isNull();

        Page<CpfTrustMemberResponse> pending = memberDirectoryService.list(null, "CPF-MBR-003", true, PageRequest.of(0, 10));
        assertThat(pending.getContent()).hasSize(1);
    }

    @Test
    void list_separatedMemberDisbursedAfterSeparation_computesLagAndExcludedFromPending() {
        Employee employee = newEmployee("EMP-CPFMBR-4", "cpfmbr4@example.com", "CPF-MBR-004", "100000000004", EmployeeStatus.RETIRED);
        ExitClearanceRequest clearance = new ExitClearanceRequest(employee, SeparationType.SUPERANNUATION, LocalDate.of(2026, 2, 1), "retirement");
        clearance.setReleaseOrderDate(LocalDate.of(2026, 2, 1));
        exitClearanceRequestRepository.save(clearance);

        TerminalSettlement settlement = draftSettlement(employee);
        settlement.setStatus(TerminalSettlementStatus.DISBURSED);
        terminalSettlementRepository.saveAndFlush(settlement);

        Page<CpfTrustMemberResponse> page = memberDirectoryService.list(null, "CPF-MBR-004", false, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        CpfTrustMemberResponse row = page.getContent().get(0);
        assertThat(row.separationDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(row.cpfSettlementStatus()).isEqualTo("DISBURSED");
        assertThat(row.cpfSettlementDate()).isNotNull();
        assertThat(row.settlementLagDays()).isNotNull().isGreaterThanOrEqualTo(0);

        Page<CpfTrustMemberResponse> pending = memberDirectoryService.list(null, "CPF-MBR-004", true, PageRequest.of(0, 10));
        assertThat(pending.getContent()).isEmpty();
    }

    @Test
    void list_statusFilter_narrowsToExactStatus() {
        newEmployee("EMP-CPFMBR-5", "cpfmbr5@example.com", "CPF-MBR-005", "100000000005", EmployeeStatus.ACTIVE);
        newEmployee("EMP-CPFMBR-6", "cpfmbr6@example.com", "CPF-MBR-006", "100000000006", EmployeeStatus.TERMINATED);

        Page<CpfTrustMemberResponse> terminatedOnly = memberDirectoryService.list("TERMINATED", null, false, PageRequest.of(0, 50));

        assertThat(terminatedOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).contains("EMP-CPFMBR-6");
        assertThat(terminatedOnly.getContent()).extracting(CpfTrustMemberResponse::employeeCode).doesNotContain("EMP-CPFMBR-5");
    }

    private TerminalSettlement draftSettlement(Employee employee) {
        return new TerminalSettlement(employee, null, SeparationType.SUPERANNUATION, LocalDate.of(2026, 2, 1),
                new BigDecimal("50000.00"), new BigDecimal("10.00"), new BigDecimal("5000.00"),
                10, 0, new BigDecimal("100.00"), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("18333.33"), new BigDecimal("4583.33"), new BigDecimal("22916.66"),
                new BigDecimal("50000.00"), false,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("77916.66"), BigDecimal.ZERO, new BigDecimal("77916.66"));
    }
}
