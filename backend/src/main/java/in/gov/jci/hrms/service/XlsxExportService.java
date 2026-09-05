package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TabularReportResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** PIMS_SPEC.md Section 3: Excel (.xlsx) rendering for the reporting hub's export button, via Apache POI. */
@Service
public class XlsxExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneOffset.UTC);

    public byte[] export(String title, TabularReportResponse report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(title));

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            CellStyle subtitleStyle = workbook.createCellStyle();
            Font subtitleFont = workbook.createFont();
            subtitleFont.setItalic(true);
            subtitleFont.setFontHeightInPoints((short) 10);
            subtitleStyle.setFont(subtitleFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle zebraStyle = workbook.createCellStyle();
            zebraStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            zebraStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;
            Row jciRow = sheet.createRow(rowIdx++);
            setCell(jciRow, 0, "The Jute Corporation of India Limited - HR Management System", titleStyle);
            Row titleRow = sheet.createRow(rowIdx++);
            setCell(titleRow, 0, title, titleStyle);
            Row genRow = sheet.createRow(rowIdx++);
            setCell(genRow, 0, "Generated: " + TIMESTAMP_FORMAT.format(Instant.now()) + " UTC", subtitleStyle);
            rowIdx++;

            List<String> columns = report.columns();
            Row headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < columns.size(); c++) {
                setCell(headerRow, c, columns.get(c), headerStyle);
            }

            List<Map<String, Object>> rows = report.rows();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(rowIdx++);
                Map<String, Object> data = rows.get(r);
                CellStyle rowStyle = (r % 2 == 1) ? zebraStyle : null;
                for (int c = 0; c < columns.size(); c++) {
                    Object value = data.get(columns.get(c));
                    setCell(row, c, value != null ? value.toString() : "", rowStyle);
                }
            }

            for (int c = 0; c < columns.size(); c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render XLSX export", e);
        }
    }

    private static void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static String sanitizeSheetName(String title) {
        String sanitized = title.replaceAll("[\\\\/*?\\[\\]:]", " ");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }
}
