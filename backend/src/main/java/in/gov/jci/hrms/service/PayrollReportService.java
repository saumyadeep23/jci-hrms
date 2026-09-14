package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.entity.Employee;
import in.gov.jci.hrms.entity.PayrollMonthlyHeadItem;
import in.gov.jci.hrms.entity.PayrollMonthlyRecord;
import in.gov.jci.hrms.entity.PayrollMonthlyStatutoryItem;
import in.gov.jci.hrms.entity.RegularPayFixation;
import in.gov.jci.hrms.exception.MasterDataNotFoundException;
import in.gov.jci.hrms.repository.PayrollBatchRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyHeadItemRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyRecordRepository;
import in.gov.jci.hrms.repository.PayrollMonthlyStatutoryItemRepository;
import in.gov.jci.hrms.repository.RegularPayFixationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill Section statutory schedule reports for one PayrollBatch, built off the real
 * payroll_salary_heads/payroll_statutory_heads catalog numbers PayrollBatchComputationService
 * actually posts against (see its own class javadoc for why these, not a draft brief's numbers, win):
 * CPF=Head 27, JCPF(employer)=stat head 3, VPF=Head 28, CPF loan principal=Head 30, TDS=Head 40,
 * Cooperative Loan=Head 46, P.Tax=Head 49, NPS(employee)=Head 62, NPS(employer)=stat head 15,
 * Recreation Club=Head 42 (V66 seed - not head 42's naive guess of P.Tax, see
 * PayrollBatchComputationService's own javadoc on this exact mix-up).
 */
@Service
@Transactional(readOnly = true)
public class PayrollReportService {

    public enum ReportType {
        SUMMARY_SHEET, CPF_SCHEDULE, INCOME_TAX_SCHEDULE, NPS_SCHEDULE, PTAX_SCHEDULE, COOPERATIVE_SCHEDULE, RECREATION_CLUB_SCHEDULE
    }

    public enum ExportFormat { JSON, CSV }

    private static final int HEAD_CPF = 27;
    private static final int HEAD_VPF = 28;
    private static final int HEAD_CPFLOAN_PRIN = 30;
    private static final int HEAD_TDS = 40;
    private static final int HEAD_RECREATION_CLUB = 42;
    private static final int HEAD_COOPERATIVE = 46;
    private static final int HEAD_PTAX = 49;
    private static final int HEAD_NPS = 62;
    private static final int STAT_HEAD_EMPLOYER_JCPF = 3;
    private static final int STAT_HEAD_EMPLOYER_NPS = 15;

    private final PayrollBatchRepository payrollBatchRepository;
    private final PayrollMonthlyRecordRepository recordRepository;
    private final PayrollMonthlyHeadItemRepository headItemRepository;
    private final PayrollMonthlyStatutoryItemRepository statutoryItemRepository;
    private final RegularPayFixationRepository regularPayFixationRepository;

    public PayrollReportService(PayrollBatchRepository payrollBatchRepository, PayrollMonthlyRecordRepository recordRepository,
                                 PayrollMonthlyHeadItemRepository headItemRepository,
                                 PayrollMonthlyStatutoryItemRepository statutoryItemRepository,
                                 RegularPayFixationRepository regularPayFixationRepository) {
        this.payrollBatchRepository = payrollBatchRepository;
        this.recordRepository = recordRepository;
        this.headItemRepository = headItemRepository;
        this.statutoryItemRepository = statutoryItemRepository;
        this.regularPayFixationRepository = regularPayFixationRepository;
    }

    public TabularReportResponse generate(Long batchId, ReportType type) {
        payrollBatchRepository.findById(batchId).orElseThrow(() -> new MasterDataNotFoundException("Payroll Batch", batchId));
        List<PayrollMonthlyRecord> records = recordRepository.findByBatch_Id(batchId);
        return switch (type) {
            case SUMMARY_SHEET -> summarySheet(records);
            case CPF_SCHEDULE -> cpfSchedule(records);
            case INCOME_TAX_SCHEDULE -> incomeTaxSchedule(records);
            case NPS_SCHEDULE -> npsSchedule(records);
            case PTAX_SCHEDULE -> ptaxSchedule(records);
            case COOPERATIVE_SCHEDULE -> cooperativeSchedule(records);
            case RECREATION_CLUB_SCHEDULE -> recreationClubSchedule(records);
        };
    }

    public byte[] renderCsv(TabularReportResponse data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8)) {
            writer.println(String.join(",", data.columns().stream().map(PayrollReportService::csvEscape).toList()));
            for (Map<String, Object> row : data.rows()) {
                List<String> cells = data.columns().stream()
                        .map(col -> csvEscape(row.get(col) != null ? row.get(col).toString() : ""))
                        .toList();
                writer.println(String.join(",", cells));
            }
        }
        return out.toByteArray();
    }

    private TabularReportResponse summarySheet(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("Department", "Cadre", "Employees", "Gross", "Deductions", "Net");
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (PayrollMonthlyRecord record : records) {
            Employee employee = record.getEmployee();
            String department = employee.getDepartment() != null ? employee.getDepartment().getName() : "-";
            String cadre = regularPayFixationRepository.findByEmployeeIdAndCurrentTrue(employee.getId())
                    .map(RegularPayFixation::getGradeScale)
                    .map(g -> g.getCadre().name())
                    .orElse("-");
            String key = department + "|" + cadre;
            Map<String, Object> row = grouped.computeIfAbsent(key, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("Department", department);
                r.put("Cadre", cadre);
                r.put("Employees", 0);
                r.put("Gross", BigDecimal.ZERO);
                r.put("Deductions", BigDecimal.ZERO);
                r.put("Net", BigDecimal.ZERO);
                return r;
            });
            row.put("Employees", (Integer) row.get("Employees") + 1);
            row.put("Gross", ((BigDecimal) row.get("Gross")).add(record.getGrossAmount()));
            row.put("Deductions", ((BigDecimal) row.get("Deductions")).add(record.getTotalDeductions()));
            row.put("Net", ((BigDecimal) row.get("Net")).add(record.getNetAmount()));
        }
        return new TabularReportResponse(columns, List.copyOf(grouped.values()), grouped.size());
    }

    private TabularReportResponse cpfSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("EmpCode", "Name", "UAN", "CPFNo", "BasicPlusDA", "EEShare", "ERShare", "VPF", "LoanPrincipal", "TotalCPF");
        List<Map<String, Object>> rows = records.stream().map(record -> {
            Employee employee = record.getEmployee();
            BigDecimal ee = headAmount(record, HEAD_CPF);
            BigDecimal er = statAmount(record, STAT_HEAD_EMPLOYER_JCPF);
            BigDecimal vpf = headAmount(record, HEAD_VPF);
            BigDecimal loanPrincipal = headAmount(record, HEAD_CPFLOAN_PRIN);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("EmpCode", record.getEmpCode());
            row.put("Name", employee.getFullName());
            row.put("UAN", employee.getUanNo());
            row.put("CPFNo", employee.getCpfAcNo());
            row.put("BasicPlusDA", record.getBasicPay());
            row.put("EEShare", ee);
            row.put("ERShare", er);
            row.put("VPF", vpf);
            row.put("LoanPrincipal", loanPrincipal);
            row.put("TotalCPF", ee.add(er));
            return row;
        }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse incomeTaxSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("EmpCode", "Name", "PAN", "GrossPay", "TDSDeducted");
        List<Map<String, Object>> rows = records.stream()
                .filter(record -> headAmount(record, HEAD_TDS).signum() > 0)
                .map(record -> {
                    Employee employee = record.getEmployee();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("EmpCode", record.getEmpCode());
                    row.put("Name", employee.getFullName());
                    row.put("PAN", employee.getPanNumber());
                    row.put("GrossPay", record.getGrossAmount());
                    row.put("TDSDeducted", headAmount(record, HEAD_TDS));
                    return row;
                }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse npsSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("EmpCode", "Name", "PRAN", "EmployeeTierI", "EmployerContribution");
        List<Map<String, Object>> rows = records.stream()
                .filter(record -> headAmount(record, HEAD_NPS).signum() > 0 || statAmount(record, STAT_HEAD_EMPLOYER_NPS).signum() > 0)
                .map(record -> {
                    Employee employee = record.getEmployee();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("EmpCode", record.getEmpCode());
                    row.put("Name", employee.getFullName());
                    row.put("PRAN", employee.getPranNumber());
                    row.put("EmployeeTierI", headAmount(record, HEAD_NPS));
                    row.put("EmployerContribution", statAmount(record, STAT_HEAD_EMPLOYER_NPS));
                    return row;
                }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse ptaxSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("EmpCode", "Name", "State", "GrossPay", "ProfessionalTax");
        List<Map<String, Object>> rows = records.stream()
                .filter(record -> headAmount(record, HEAD_PTAX).signum() > 0)
                .map(record -> {
                    Employee employee = record.getEmployee();
                    String state = employee.getRegionalOffice() != null ? employee.getRegionalOffice().getState() : null;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("EmpCode", record.getEmpCode());
                    row.put("Name", employee.getFullName());
                    row.put("State", state);
                    row.put("GrossPay", record.getGrossAmount());
                    row.put("ProfessionalTax", headAmount(record, HEAD_PTAX));
                    return row;
                }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse cooperativeSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("MemberID", "Name", "RecoveryAmount");
        List<Map<String, Object>> rows = records.stream()
                .filter(record -> headAmount(record, HEAD_COOPERATIVE).signum() > 0)
                .map(record -> {
                    Employee employee = record.getEmployee();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("MemberID", record.getEmpCode());
                    row.put("Name", employee.getFullName());
                    row.put("RecoveryAmount", headAmount(record, HEAD_COOPERATIVE));
                    return row;
                }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse recreationClubSchedule(List<PayrollMonthlyRecord> records) {
        List<String> columns = List.of("MemberID", "Name", "SubscriptionAmount");
        List<Map<String, Object>> rows = records.stream()
                .filter(record -> headAmount(record, HEAD_RECREATION_CLUB).signum() > 0)
                .map(record -> {
                    Employee employee = record.getEmployee();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("MemberID", record.getEmpCode());
                    row.put("Name", employee.getFullName());
                    row.put("SubscriptionAmount", headAmount(record, HEAD_RECREATION_CLUB));
                    return row;
                }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private BigDecimal headAmount(PayrollMonthlyRecord record, int headCount) {
        return headItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .filter(item -> item.getHeadCount() == headCount)
                .map(PayrollMonthlyHeadItem::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private BigDecimal statAmount(PayrollMonthlyRecord record, int statHeadCount) {
        return statutoryItemRepository.findByRecord_TranId(record.getTranId()).stream()
                .filter(item -> item.getStatHeadCount() == statHeadCount)
                .map(PayrollMonthlyStatutoryItem::getAmount)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
