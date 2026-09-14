package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.EditPayrollLineDto;
import in.gov.jci.hrms.dto.PayrollEditResponse;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.entity.Cadre;
import in.gov.jci.hrms.entity.DaRateHistory;
import in.gov.jci.hrms.entity.Department;
import in.gov.jci.hrms.entity.Designation;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.GradeScaleMaster;
import in.gov.jci.hrms.entity.PayrollBatch;
import in.gov.jci.hrms.entity.PayrollBatchStatus;
import in.gov.jci.hrms.entity.PayrollHraRate;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.entity.ScaleType;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import in.gov.jci.hrms.repository.DaRateHistoryRepository;
import in.gov.jci.hrms.repository.DepartmentRepository;
import in.gov.jci.hrms.repository.DesignationRepository;
import in.gov.jci.hrms.repository.EmployeeRepository;
import in.gov.jci.hrms.repository.GradeScaleMasterRepository;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollEditLogRepository;
import in.gov.jci.hrms.repository.PayrollHraRateRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB integration test (same pattern as CpfLedgerSyncServiceTest) covering the two new payroll
 * capabilities built on top of PayrollBatchComputationService: PayrollBatchEditService's BASIC-edit
 * cascade (DA/HRA/CPF/JCPF/P.Tax, plus payroll_edit_logs audit rows and the DRAFT/CALCULATED-only
 * lock) and PayrollReportService's statutory schedule reports.
 */
@SpringBootTest
@Transactional
class PayrollBatchCascadeAndReportsTest {

    private static final int HEAD_BASIC = 1;
    private static final int HEAD_DA_IDA = 8;
    private static final int HEAD_HRA = 9;
    private static final int HEAD_CPF = 27;
    private static final int HEAD_PTAX = 49;
    private static final int STAT_HEAD_EPF = 1;
    private static final int STAT_HEAD_JCPF = 3;
    private static final int STAT_HEAD_PENSION = 4;

    @Autowired private PayrollBatchEditService payrollBatchEditService;
    @Autowired private PayrollReportService payrollReportService;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private GradeScaleMasterRepository gradeScaleMasterRepository;
    @Autowired private RegularPayFixationRepository regularPayFixationRepository;
    @Autowired private DaRateHistoryRepository daRateHistoryRepository;
    @Autowired private PayrollHraRateRepository payrollHraRateRepository;
    @Autowired private PayrollBatchRepository payrollBatchRepository;
    @Autowired private PayrollMonthlyRecordRepository recordRepository;
    @Autowired private PayrollMonthlyHeadItemRepository headItemRepository;
    @Autowired private PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    @Autowired private PayrollEditLogRepository editLogRepository;

    private Employee employee;
    private PayrollBatch batch;
    private PayrollMonthlyRecord record;

    @BeforeEach
    void setUp() {
        Department department = departmentRepository.save(new Department("EDIT", "Edit Cascade Test Dept"));
        Designation designation = designationRepository.save(new Designation("Edit Cascade Test Officer"));
        employee = employeeRepository.save(new Employee("EMP-EDITCASC-1", "Nirmal", "Ghosh",
                "nirmal.ghosh.editcasc@example.com", LocalDate.of(2018, 4, 1), department, designation));

        GradeScaleMaster gradeScale = gradeScaleMasterRepository.save(
                new GradeScaleMaster("E2-EDIT", Cadre.EXECUTIVE, 9421, false, new BigDecimal("30000.00"), new BigDecimal("90000.00")));
        gradeScale.setScaleType(ScaleType.IDA);
        gradeScaleMasterRepository.save(gradeScale);

        regularPayFixationRepository.save(new RegularPayFixation(employee, gradeScale, new BigDecimal("50000.00"), LocalDate.of(2018, 4, 1)));
        // Distinctive effective_from dates (not the usual 1-April revision date) to avoid colliding with
        // real seeded DA/HRA rate rows under this table's own UNIQUE(scale_type/city_class, effective_from) constraint -
        // both LessThanEqual queries then pick these as the most-recent rate as-of this test's Aug-2026 period end.
        daRateHistoryRepository.save(new DaRateHistory(ScaleType.IDA, LocalDate.of(2026, 8, 15), new BigDecimal("17.00"), true));
        payrollHraRateRepository.save(new PayrollHraRate("Z", new BigDecimal("10.00"), BigDecimal.ZERO, LocalDate.of(2026, 8, 15), null, null));

        batch = payrollBatchRepository.save(new PayrollBatch("BATCH-EDITCASC-1", 8, 2026, "2026-2027"));
        batch.setStatus(PayrollBatchStatus.CALCULATED);
        payrollBatchRepository.save(batch);

        record = recordRepository.save(new PayrollMonthlyRecord(batch, employee, employee.getEmployeeCode(),
                8, 2026, null, null, "Z", "IDA", 31));
        record.setBasicPay(new BigDecimal("50000.00"));
        record.setGrossAmount(new BigDecimal("63500.00")); // 50000 + DA 8500 + HRA 5000
        record.setTotalDeductions(new BigDecimal("7020.00")); // CPF 12% of 58500
        record.setNetAmount(new BigDecimal("56480.00"));
        recordRepository.save(record);

        headItemRepository.save(new PayrollMonthlyHeadItem(record, HEAD_BASIC, new BigDecimal("50000.00")));
        headItemRepository.save(new PayrollMonthlyHeadItem(record, HEAD_DA_IDA, new BigDecimal("8500.00")));
        headItemRepository.save(new PayrollMonthlyHeadItem(record, HEAD_HRA, new BigDecimal("5000.00")));
        headItemRepository.save(new PayrollMonthlyHeadItem(record, HEAD_CPF, new BigDecimal("7020.00")));
        statutoryItemRepository.save(new PayrollMonthlyStatutoryItem(record, STAT_HEAD_JCPF, new BigDecimal("7020.00")));
    }

    @Test
    void editBasicPay_cascadesDaHraCpfEpfJcpfAndLogsEveryChangedHead() {
        EditPayrollLineDto dto = new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("60000.00"), "Correction per revised pay order");

        PayrollEditResponse response = payrollBatchEditService.editSalaryHead(batch.getId(), record.getTranId(), dto, null);

        // New Basic 60000; DA = round(60000*17%) = 10200; HRA = round(60000*10%) = 6000;
        // Basic+DA = 70200; CPF = round(70200*12%) = 8424; P.Tax = 0 (no RegionalOffice on this employee).
        // Employee is not EPS-eligible, so Pension (stat 4) stays 0 and JCPF (stat 3) absorbs the full CPF,
        // same as EPF (stat 1), which always mirrors Head 27 unconditionally.
        assertThat(response.grossAmount()).isEqualByComparingTo("76200.00");
        assertThat(response.totalDeductions()).isEqualByComparingTo("8424.00");
        assertThat(response.netAmount()).isEqualByComparingTo("67776.00");

        PayrollMonthlyRecord reloaded = recordRepository.findById(record.getTranId()).orElseThrow();
        assertThat(reloaded.getBasicPay()).isEqualByComparingTo("60000.00");
        assertThat(reloaded.getGrossAmount()).isEqualByComparingTo("76200.00");
        assertThat(reloaded.getTotalDeductions()).isEqualByComparingTo("8424.00");
        assertThat(reloaded.getNetAmount()).isEqualByComparingTo("67776.00");

        assertThat(headAmount(HEAD_DA_IDA)).isEqualByComparingTo("10200.00");
        assertThat(headAmount(HEAD_HRA)).isEqualByComparingTo("6000.00");
        assertThat(headAmount(HEAD_CPF)).isEqualByComparingTo("8424.00");
        assertThat(statAmount(STAT_HEAD_EPF)).isEqualByComparingTo("8424.00");
        assertThat(statAmount(STAT_HEAD_PENSION)).isZero();
        assertThat(statAmount(STAT_HEAD_JCPF)).isEqualByComparingTo("8424.00");

        List<in.gov.jci.hrms.entity.PayrollEditLog> logs = editLogRepository.findByRecord_TranIdOrderByEditedAtDesc(record.getTranId());
        assertThat(logs).hasSize(8); // BASIC, DA, HRA, CPF, EPF(stat), PENSION(stat), JCPF(stat), P.Tax
        assertThat(logs).filteredOn(l -> l.getHeadCount() == HEAD_BASIC)
                .allSatisfy(l -> assertThat(l.getChangeReason()).isEqualTo("Correction per revised pay order"));
        assertThat(logs).filteredOn(l -> l.getHeadCount() == HEAD_DA_IDA)
                .allSatisfy(l -> {
                    assertThat(l.getChangeReason()).isEqualTo("Cascaded from Basic Pay modification");
                    assertThat(l.getOldAmount()).isEqualByComparingTo("8500.00");
                    assertThat(l.getNewAmount()).isEqualByComparingTo("10200.00");
                });
    }

    @Test
    void previewSalaryHead_doesNotPersist() {
        EditPayrollLineDto dto = new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("60000.00"), "preview only");

        PayrollEditResponse preview = payrollBatchEditService.previewSalaryHead(batch.getId(), record.getTranId(), dto);

        assertThat(preview.grossAmount()).isEqualByComparingTo("76200.00");
        assertThat(headAmount(HEAD_BASIC)).isEqualByComparingTo("50000.00"); // unchanged in the DB
        assertThat(editLogRepository.findByRecord_TranIdOrderByEditedAtDesc(record.getTranId())).isEmpty();
    }

    @Test
    void editSalaryHead_onFinalizedBatch_throwsAndLeavesDataUntouched() {
        batch.setStatus(PayrollBatchStatus.HR_FINALIZED);
        payrollBatchRepository.save(batch);
        EditPayrollLineDto dto = new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("99999.00"), "should be rejected");

        assertThatThrownBy(() -> payrollBatchEditService.editSalaryHead(batch.getId(), record.getTranId(), dto, null))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(headAmount(HEAD_BASIC)).isEqualByComparingTo("50000.00");
    }

    @Test
    void cpfSchedule_reflectsEeAndErShareAfterEdit() {
        payrollBatchEditService.editSalaryHead(batch.getId(), record.getTranId(),
                new EditPayrollLineDto(HEAD_BASIC, new BigDecimal("60000.00"), "revision"), null);

        TabularReportResponse report = payrollReportService.generate(batch.getId(), PayrollReportService.ReportType.CPF_SCHEDULE);

        assertThat(report.columns()).contains("EEShare", "ERShare", "TotalCPF");
        assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().get(0).get("EEShare")).isEqualTo(new BigDecimal("8424.00"));
        assertThat(report.rows().get(0).get("ERShare")).isEqualTo(new BigDecimal("8424.00"));
    }

    @Test
    void ptaxSchedule_emptyWhenNoProfessionalTaxPosted() {
        TabularReportResponse report = payrollReportService.generate(batch.getId(), PayrollReportService.ReportType.PTAX_SCHEDULE);

        assertThat(report.rows()).isEmpty();
        assertThat(headAmount(HEAD_PTAX)).isZero();
    }

    @Test
    void summarySheet_groupsByDepartmentAndCadre() {
        TabularReportResponse report = payrollReportService.generate(batch.getId(), PayrollReportService.ReportType.SUMMARY_SHEET);

        assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().get(0).get("Department")).isEqualTo("Edit Cascade Test Dept");
        assertThat(report.rows().get(0).get("Cadre")).isEqualTo("EXECUTIVE");
        assertThat(report.rows().get(0).get("Employees")).isEqualTo(1);
    }

    @Test
    void renderCsv_producesHeaderRowAndOneDataRow() {
        TabularReportResponse report = payrollReportService.generate(batch.getId(), PayrollReportService.ReportType.SUMMARY_SHEET);

        byte[] csv = payrollReportService.renderCsv(report);
        String text = new String(csv, StandardCharsets.UTF_8);

        assertThat(text).contains("Department,Cadre,Employees,Gross,Deductions,Net");
        assertThat(text.lines().count()).isEqualTo(2); // header + 1 data row
    }

    private BigDecimal headAmount(int headCount) {
        return headItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .filter(item -> item.getHeadCount() == headCount)
                .map(PayrollMonthlyHeadItem::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private BigDecimal statAmount(int statHeadCount) {
        return statutoryItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .filter(item -> item.getStatHeadCount() == statHeadCount)
                .map(PayrollMonthlyStatutoryItem::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }
}
