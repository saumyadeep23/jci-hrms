package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementOrder;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.Canvas;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.PdfFonts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Ref pattern "JCI/Promotion/{FY}/Pers.(R)" is whatever MovementOrder.orderRefNo was entered as. Used for both PROMOTION and TRANSFER_CUM_PROMOTION orders. */
@Component
public class PromotionOrderPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final List<String> MANDATORY_CLAUSES = List.of(
            "They shall continue to hold their present charge(s) until properly handed over to their successor(s).",
            "They will be on probation for a period of 6 (six) months from the date of joining the new post, subject to confirmation in writing.",
            "They shall acquire mandatory computer conversance (including use of email, JCI's online software, and data storage/retrieval) "
                    + "within 6 (six) months from the date of assumption of charge.",
            "They shall be required to acquire working knowledge in Hindi.",
            "All other terms and conditions of service shall remain unaltered as per their initial Offer of Appointment.");

    private final PdfHeaderFooterHelper helper;

    public PromotionOrderPdfGenerator(PdfHeaderFooterHelper helper) {
        this.helper = helper;
    }

    public byte[] generate(MovementOrder order, List<EmployeeMovementRecord> records) {
        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = helper.loadFonts(document);
            try (Canvas canvas = helper.newCanvas(document, fonts)) {
                canvas.line("Ref: " + order.getOrderRefNo(), fonts.english(), 9);
                canvas.line("Date: " + DATE_FORMAT.format(order.getOrderDate()), fonts.english(), 9);
                canvas.gap(6);

                canvas.bilingualTitle("OFFICE ORDER", "कार्यालय आदेश");
                canvas.gap(4);

                canvas.bilingualParagraph(introText(records), null, 10);

                canvas.table(
                        List.of("Sl. No.", "Name (Emp No)", "Present Place of Posting", "Transferred To"),
                        tableRows(records),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.08f, 0.32f, 0.3f, 0.3f));

                canvas.gap(6);
                for (String clause : MANDATORY_CLAUSES) {
                    canvas.wrapped("• " + clause, fonts.english(), 9.5f);
                    canvas.gap(3);
                }

                canvas.signatureBlock("Manager (HR)");
                canvas.gap(10);
                canvas.distributionList(PdfHeaderFooterHelper.DISTRIBUTION_STANDARD);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render Promotion Order PDF", e);
        }
    }

    private String introText(List<EmployeeMovementRecord> records) {
        if (records.isEmpty()) {
            return "Selected for promotion to the grade noted below, in the corresponding IDA Pay Scale, "
                    + "w.e.f. date of assumption of charge at their respective posting.";
        }
        EmployeeMovementRecord first = records.get(0);
        String grade = first.getToDesignation().getTitle();
        String payScale = first.getToPayScale() != null ? first.getToPayScale() : "as per the revised grade";
        return "Selected for promotion to " + grade + " in IDA Pay Scale (" + payScale
                + ") w.e.f. date of assumption of charge at their respective posting.";
    }

    private List<List<String>> tableRows(List<EmployeeMovementRecord> records) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            EmployeeMovementRecord r = records.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    r.getEmployee().getFullName() + " (" + r.getEmployee().getEmployeeCode() + ")",
                    r.getFromOffice().getName(),
                    r.getToOffice().getName()));
        }
        return rows;
    }
}
