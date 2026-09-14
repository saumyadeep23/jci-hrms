package in.gov.jci.hrms.service;

import in.gov.jci.hrms.entity.CpfLedgerEntryType;
import in.gov.jci.hrms.entity.CpfTrustMemberLedgerEntry;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CpfContributionBreakdownService - the single authoritative EPS/contribution-summary source (Part 16 of
 * the Passbook V2 module spec). EPS here is read straight off payroll_monthly_statutory_items'
 * stat_head_count=4 ("Pension Fund") rows this test seeds directly - never derived/recomputed by the class
 * under test, matching PayrollBatchComputationService.resolveEmployerContributions()'s own established
 * head-4-is-the-EPS-carve-out convention (verified by inspection before writing this class - see
 * CpfContributionBreakdownService's own javadoc).
 */
@SpringBootTest
@Transactional
class CpfContributionBreakdownServiceTest {

    private static final int STAT_HEAD_JCPF = 3;
    private static final int STAT_HEAD_PENSION_EPS = 4;
    private static final String FIN_YEAR = "2025-2026";

    @Autowired private CpfContributionBreakdownService contributionBreakdownService;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private CpfTrustMemberLedgerEntryRepository ledgerRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository payrollMonthlyRecordRepository;
    @Autowired private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("CPFEPS", "CPF EPS Test Dept"));
        Designation designation = designationRepository.save(new Designation("CPF EPS Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-CPFEPS-1", "Eps", "Tester", "cpfeps1@example.com",
                LocalDate.of(2015, 1, 1), department, designation));
    }

    private PayrollMonthlyRecord seedPayrollMonth(int month, int year, BigDecimal jcpf, BigDecimal eps) {
        PayrollBatch batch = payrollBatchRepository.save(new PayrollBatch("BATCH-EPS-" + year + "-" + month, month, year, FIN_YEAR));
        PayrollMonthlyRecord record = payrollMonthlyRecordRepository.save(
                new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(), month, year, "0001", "OFF", "X", "FULL", 30));
        if (jcpf.signum() > 0) {
            statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, STAT_HEAD_JCPF, jcpf));
        }
        if (eps.signum() > 0) {
            statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, STAT_HEAD_PENSION_EPS, eps));
        }
        return record;
    }

    private CpfTrustMemberLedgerEntry seedLedgerRow(int month, int year, BigDecimal ee, BigDecimal er, BigDecimal vpf, BigDecimal priorTotal) {
        LocalDate valueDate = java.time.YearMonth.of(year, month).atEndOfMonth();
        CpfTrustMemberLedgerEntry entry = new CpfTrustMemberLedgerEntry(employee, FIN_YEAR, valueDate, CpfLedgerEntryType.PAYROLL_MONTHLY,
                ee, er, vpf, ee.add(er).add(vpf).add(priorTotal));
        entry.setEeShareCredit(ee);
        entry.setErShareCredit(er);
        entry.setVpfCredit(vpf);
        entry.setSalMonth(month);
        entry.setSalYear(year);
        return ledgerRepository.save(entry);
    }

    @Test
    void epsForFinYear_sumsStoredHead4AcrossTheFinancialYearsTwoCalendarYears() {
        // April (first FY calendar year) and February (second FY calendar year) - proves the FY-spanning join works.
        seedPayrollMonth(4, 2025, new BigDecimal("1500.00"), new BigDecimal("500.00"));
        seedPayrollMonth(2, 2026, new BigDecimal("1500.00"), new BigDecimal("500.00"));
        // A month outside this FY must never be included.
        seedPayrollMonth(4, 2024, new BigDecimal("1500.00"), new BigDecimal("9999.00"));

        BigDecimal eps = contributionBreakdownService.epsForFinYear(employee.getId(), FIN_YEAR);

        assertThat(eps).isEqualByComparingTo("1000.00");
    }

    @Test
    void epsForFinYear_noPayrollRecords_isZero_notFabricated() {
        BigDecimal eps = contributionBreakdownService.epsForFinYear(employee.getId(), "2030-2031");
        assertThat(eps).isEqualByComparingTo("0");
    }

    @Test
    void summaryFor_epsIsAdditive_neverDoubleCountedOrSubtractedFromEmployer() {
        seedPayrollMonth(4, 2025, new BigDecimal("1800.00"), new BigDecimal("450.00"));
        CpfTrustMemberLedgerEntry ledgerRow = seedLedgerRow(4, 2025, new BigDecimal("2000.00"), new BigDecimal("1800.00"), new BigDecimal("300.00"), BigDecimal.ZERO);

        var summary = contributionBreakdownService.summaryFor(employee.getId(), FIN_YEAR, ledgerRepository.findByEmployee_IdAndFinYearOrderByValueDateAscIdAsc(employee.getId(), FIN_YEAR));

        // Employee/Employer/VPF come straight from the ledger row (already-posted, authoritative figures);
        // EPS is looked up separately - the employer figure is NOT reduced by it (it's already net of EPS
        // by construction, per PayrollBatchComputationService's own established split).
        assertThat(summary.employeeContribution()).isEqualByComparingTo("2000.00");
        assertThat(summary.employerContribution()).isEqualByComparingTo("1800.00");
        assertThat(summary.vpfContribution()).isEqualByComparingTo("300.00");
        assertThat(summary.epsContribution()).isEqualByComparingTo("450.00");
        // Total = EE + ER + EPS + VPF exactly once each - no double count.
        assertThat(summary.totalContribution())
                .isEqualByComparingTo(new BigDecimal("2000.00").add(new BigDecimal("1800.00")).add(new BigDecimal("450.00")).add(new BigDecimal("300.00")));
    }

    @Test
    void epsByLedgerEntry_onlyMapsPayrollRowsWithSalMonthYear_neverFabricatesForOtherTypes() {
        seedPayrollMonth(4, 2025, new BigDecimal("1800.00"), new BigDecimal("450.00"));
        CpfTrustMemberLedgerEntry payrollRow = seedLedgerRow(4, 2025, new BigDecimal("2000.00"), new BigDecimal("1800.00"), BigDecimal.ZERO, BigDecimal.ZERO);

        CpfTrustMemberLedgerEntry interestRow = new CpfTrustMemberLedgerEntry(employee, FIN_YEAR, LocalDate.of(2026, 3, 31),
                CpfLedgerEntryType.ANNUAL_INTEREST, new BigDecimal("2100.00"), new BigDecimal("1890.00"), BigDecimal.ZERO, new BigDecimal("3990.00"));
        ledgerRepository.save(interestRow);

        var epsMap = contributionBreakdownService.epsByLedgerEntry(employee.getId(), java.util.List.of(payrollRow, interestRow));

        assertThat(epsMap).containsEntry(payrollRow.getId(), new BigDecimal("450.00"));
        assertThat(epsMap).doesNotContainKey(interestRow.getId()); // not applicable, never fabricated as zero
    }
}
