package in.gov.jci.hrms.service;

import in.gov.jci.hrms.dto.TabularReportResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * PIMS_SPEC.md Section 3: PDF rendering for the reporting hub's export
 * button, A4 landscape with a JCI header block, "Page X of Y" footers, and
 * a diagonal generation-timestamp watermark, via Apache PDFBox. PDFBox has
 * no built-in table widget, so this lays out an equal-width column grid by
 * hand and paginates by a fixed row height.
 */
@Service
public class PdfExportService {

    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 30f;
    private static final float ROW_HEIGHT = 16f;
    private static final float HEADER_BLOCK_HEIGHT = 55f;
    private static final float FOOTER_HEIGHT = 20f;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZoneOffset.UTC);

    public byte[] export(String title, TabularReportResponse report) {
        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            float pageWidth = PAGE_SIZE.getWidth();
            float pageHeight = PAGE_SIZE.getHeight();
            float tableTop = pageHeight - MARGIN - HEADER_BLOCK_HEIGHT;
            float tableBottom = MARGIN + FOOTER_HEIGHT;
            int rowsPerPage = Math.max(1, (int) ((tableTop - tableBottom - ROW_HEIGHT) / ROW_HEIGHT));

            List<String> columns = report.columns();
            List<Map<String, Object>> rows = report.rows();
            int totalPages = Math.max(1, (int) Math.ceil(rows.size() / (double) rowsPerPage));
            float colWidth = (pageWidth - 2 * MARGIN) / Math.max(1, columns.size());
            int maxCharsPerCol = Math.max(3, (int) (colWidth / 5.2f));

            String generatedAt = TIMESTAMP_FORMAT.format(Instant.now()) + " UTC";

            for (int pageIndex = 0; pageIndex < totalPages; pageIndex++) {
                PDPage page = new PDPage(PAGE_SIZE);
                document.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                    drawWatermark(document, cs, pageWidth, pageHeight, generatedAt, regularFont);

                    float y = pageHeight - MARGIN;
                    cs.beginText();
                    cs.setFont(boldFont, 14);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText("The Jute Corporation of India Limited - HR Management System");
                    cs.endText();

                    y -= 18;
                    cs.beginText();
                    cs.setFont(boldFont, 12);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText(title);
                    cs.endText();

                    y -= 14;
                    cs.beginText();
                    cs.setFont(regularFont, 9);
                    cs.newLineAtOffset(MARGIN, y);
                    cs.showText("Generated: " + generatedAt);
                    cs.endText();

                    float rowY = tableTop;
                    drawRow(cs, columns, MARGIN, rowY, colWidth, boldFont, 9, maxCharsPerCol);
                    rowY -= ROW_HEIGHT;

                    int start = pageIndex * rowsPerPage;
                    int end = Math.min(rows.size(), start + rowsPerPage);
                    for (int r = start; r < end; r++) {
                        Map<String, Object> data = rows.get(r);
                        List<String> values = columns.stream().map(c -> {
                            Object v = data.get(c);
                            return v != null ? v.toString() : "";
                        }).toList();
                        drawRow(cs, values, MARGIN, rowY, colWidth, regularFont, 8, maxCharsPerCol);
                        rowY -= ROW_HEIGHT;
                    }

                    String footerText = "Page " + (pageIndex + 1) + " of " + totalPages;
                    float footerWidth = regularFont.getStringWidth(footerText) / 1000 * 9;
                    cs.beginText();
                    cs.setFont(regularFont, 9);
                    cs.newLineAtOffset((pageWidth - footerWidth) / 2, MARGIN / 2);
                    cs.showText(footerText);
                    cs.endText();
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render PDF export", e);
        }
    }

    private static void drawRow(PDPageContentStream cs, List<String> values, float x, float y, float colWidth, PDFont font, int fontSize,
                                 int maxChars) throws IOException {
        float cellX = x;
        for (String value : values) {
            String truncated = value.length() > maxChars ? value.substring(0, Math.max(0, maxChars - 1)) + "..." : value;
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(cellX, y);
            cs.showText(truncated);
            cs.endText();
            cellX += colWidth;
        }
    }

    private static void drawWatermark(PDDocument document, PDPageContentStream cs, float pageWidth, float pageHeight, String text, PDFont font)
            throws IOException {
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(0.08f);
        cs.setGraphicsStateParameters(gs);
        cs.beginText();
        cs.setFont(font, 48);
        // setNonStrokingColor(float,float,float) is DeviceRGB and expects each component in 0..1,
        // not the 0..255 scale a "120,120,120" gray usually implies - PDFBox throws
        // IllegalArgumentException otherwise (this broke every PDF export until fixed).
        cs.setNonStrokingColor(120 / 255f, 120 / 255f, 120 / 255f);
        cs.setTextMatrix(org.apache.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(30), pageWidth / 5, pageHeight / 3));
        cs.showText(text);
        cs.endText();

        PDExtendedGraphicsState resetGs = new PDExtendedGraphicsState();
        resetGs.setNonStrokingAlphaConstant(1.0f);
        cs.setGraphicsStateParameters(resetGs);
    }
}
