package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.Circular53ComplianceRow;
import in.gov.jci.hrms.dto.CcsForm1Response;
import in.gov.jci.hrms.dto.EncashmentRegisterRow;
import in.gov.jci.hrms.dto.MusterRollRow;
import in.gov.jci.hrms.dto.PayrollCutoffFeedRow;
import in.gov.jci.hrms.dto.TabularReportResponse;
import in.gov.jci.hrms.exception.BusinessRuleViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders any of the 5 ALMS reports to XLSX (via the existing
 * XlsxExportService/TabularReportResponse pipeline - PIMS_SPEC.md's
 * reporting-hub infrastructure, reused rather than duplicated) or a flat
 * CSV (hand-written; adding a dependency like OpenCSV for a single flat
 * comma-join isn't warranted here).
 */
@Service
public class AlmsReportExportService {

    public enum AlmsReportType { MUSTER_ROLL, PAYROLL_FEED, CIRCULAR53, CCS_FORM1, ENCASHMENT_REGISTER }

    public enum ExportFormat { XLSX, CSV }

    private final AlmsReportService almsReportService;
    private final XlsxExportService xlsxExportService;

    public AlmsReportExportService(AlmsReportService almsReportService, XlsxExportService xlsxExportService) {
        this.almsReportService = almsReportService;
        this.xlsxExportService = xlsxExportService;
    }

    public byte[] export(AlmsReportType type, ExportFormat format, LocalDate startDate, LocalDate endDate,
                          Long officeId, String cadre, YearMonth yearMonth, Long employeeId, Integer year) {
        TabularReportResponse data = switch (type) {
            case MUSTER_ROLL -> musterRollTable(requireDate(startDate, "startDate"), requireDate(endDate, "endDate"), officeId, cadre);
            case PAYROLL_FEED -> payrollFeedTable(requireDate(startDate, "startDate"), requireDate(endDate, "endDate"), officeId);
            case CIRCULAR53 -> circular53Table(requireNonNull(yearMonth, "yearMonth"), officeId);
            case CCS_FORM1 -> ccsForm1Table(requireNonNull(employeeId, "employeeId"), requireNonNull(year, "year"));
            case ENCASHMENT_REGISTER -> encashmentRegisterTable(requireDate(startDate, "startDate"), requireDate(endDate, "endDate"));
        };
        String title = type.name().replace('_', ' ') + " Report";
        return format == ExportFormat.XLSX ? xlsxExportService.export(title, data) : toCsv(data);
    }

    private TabularReportResponse musterRollTable(LocalDate start, LocalDate end, Long officeId, String cadre) {
        Page<MusterRollRow> page = almsReportService.generateMusterRoll(start, end, officeId, cadre, PageRequest.of(0, 5000));
        List<String> dayColumns = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) dayColumns.add(String.valueOf(d.getDayOfMonth()));
        List<String> columns = new ArrayList<>(List.of("Employee Code", "Employee Name", "Designation", "Office", "State"));
        columns.addAll(dayColumns);
        columns.addAll(List.of("Present", "Paid Leave", "WO/Holiday", "LWP", "Penalty Days", "Net Payable Days"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MusterRollRow r : page.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", r.employeeCode());
            row.put("Employee Name", r.employeeName());
            row.put("Designation", r.designation());
            row.put("Office", r.officeName());
            row.put("State", r.state());
            for (String day : dayColumns) row.put(day, r.dailyPunches().getOrDefault(day, ""));
            row.put("Present", r.presentDays());
            row.put("Paid Leave", r.paidLeaveDays());
            row.put("WO/Holiday", r.weeklyOffsAndHolidays());
            row.put("LWP", r.unpaidLwpDays());
            row.put("Penalty Days", r.penaltyDeductionDays());
            row.put("Net Payable Days", r.netPayableDays());
            rows.add(row);
        }
        return new TabularReportResponse(columns, rows, page.getTotalElements());
    }

    private TabularReportResponse payrollFeedTable(LocalDate start, LocalDate end, Long officeId) {
        List<PayrollCutoffFeedRow> data = almsReportService.generatePayrollFeed(start, end, officeId);
        List<String> columns = List.of("Employee Code", "Employee Name", "Period Start", "Period End", "Cycle Days",
                "Payable Days", "LWP Days", "Absent Days", "Penalty Days", "Approved In-Service EL", "Locked");
        List<Map<String, Object>> rows = data.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", r.employeeCode());
            row.put("Employee Name", r.employeeName());
            row.put("Period Start", r.periodStart());
            row.put("Period End", r.periodEnd());
            row.put("Cycle Days", r.totalCycleDays());
            row.put("Payable Days", r.payableDays());
            row.put("LWP Days", r.lwpDays());
            row.put("Absent Days", r.absentDays());
            row.put("Penalty Days", r.penaltyDays());
            row.put("Approved In-Service EL", r.approvedInServiceElDays());
            row.put("Locked", r.isLocked());
            return row;
        }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse circular53Table(YearMonth yearMonth, Long officeId) {
        List<Circular53ComplianceRow> data = almsReportService.generateCircular53Report(yearMonth, officeId);
        List<String> columns = List.of("Employee Code", "Employee Name", "Office", "Month", "Flex Grace",
                "Late Concessions", "Early Concessions", "3rd Strike (Unauthorized)", "Penalty Days Debited");
        List<Map<String, Object>> rows = data.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Employee Code", r.employeeCode());
            row.put("Employee Name", r.employeeName());
            row.put("Office", r.officeName());
            row.put("Month", r.monthYear());
            row.put("Flex Grace", r.flexGraceCount());
            row.put("Late Concessions", r.concessionLateCount());
            row.put("Early Concessions", r.concessionEarlyCount());
            row.put("3rd Strike (Unauthorized)", r.thirdStrikeUnregularizedCount());
            row.put("Penalty Days Debited", r.penaltyLeaveDebited());
            return row;
        }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse ccsForm1Table(Long employeeId, Integer year) {
        CcsForm1Response r = almsReportService.generateCcsForm1(employeeId, year);
        List<String> columns = List.of("From", "To", "Type", "Enjoyable Debited", "Encashable Debited", "Order Ref");
        List<Map<String, Object>> rows = r.availedTransactions().stream().map(t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("From", t.fromDate());
            row.put("To", t.toDate());
            row.put("Type", t.type());
            row.put("Enjoyable Debited", t.enjoyableDebited());
            row.put("Encashable Debited", t.encashableDebited());
            row.put("Order Ref", t.orderRef());
            return row;
        }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private TabularReportResponse encashmentRegisterTable(LocalDate from, LocalDate to) {
        List<EncashmentRegisterRow> data = almsReportService.generateEncashmentRegister(from, to);
        List<String> columns = List.of("Application #", "Employee Code", "Employee Name", "Type", "Qualifying Tenure",
                "Days Claimed", "HR Approved By", "HR Approved At", "Finance Approved By", "Finance Approved At",
                "Payroll Eligible", "Service Book Folio", "Estimated Amount");
        List<Map<String, Object>> rows = data.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Application #", r.applicationNumber());
            row.put("Employee Code", r.employeeCode());
            row.put("Employee Name", r.employeeName());
            row.put("Type", r.encashmentType());
            row.put("Qualifying Tenure", r.qualifyingTenure());
            row.put("Days Claimed", r.daysClaimed());
            row.put("HR Approved By", r.hrApprovedBy());
            row.put("HR Approved At", r.hrApprovedAt());
            row.put("Finance Approved By", r.financeApprovedBy());
            row.put("Finance Approved At", r.financeApprovedAt());
            row.put("Payroll Eligible", r.isPayrollEligible());
            row.put("Service Book Folio", r.serviceBookFolio());
            row.put("Estimated Amount", r.estimatedAmount());
            return row;
        }).toList();
        return new TabularReportResponse(columns, rows, rows.size());
    }

    private byte[] toCsv(TabularReportResponse data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8)) {
            writer.println(String.join(",", data.columns().stream().map(AlmsReportExportService::csvEscape).toList()));
            for (Map<String, Object> row : data.rows()) {
                List<String> cells = data.columns().stream()
                        .map(col -> csvEscape(row.get(col) != null ? row.get(col).toString() : ""))
                        .toList();
                writer.println(String.join(",", cells));
            }
        }
        return out.toByteArray();
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static LocalDate requireDate(LocalDate value, String name) {
        if (value == null) throw new BusinessRuleViolationException(name + " is required for this export");
        return value;
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) throw new BusinessRuleViolationException(name + " is required for this export");
        return value;
    }
}
