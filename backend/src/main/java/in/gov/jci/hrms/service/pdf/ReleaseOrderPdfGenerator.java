package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.Canvas;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.PdfFonts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Ref pattern "JCI/Pers./HO/Release/{FY}" is EmployeeMovementRecord.releaseOrderRef, as entered when the record was released (Tab 2). */
@Component
public class ReleaseOrderPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PdfHeaderFooterHelper helper;

    public ReleaseOrderPdfGenerator(PdfHeaderFooterHelper helper) {
        this.helper = helper;
    }

    public byte[] generate(EmployeeMovementRecord record) {
        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = helper.loadFonts(document);
            try (Canvas canvas = helper.newCanvas(document, fonts)) {
                canvas.line("Ref: " + record.getReleaseOrderRef(), fonts.english(), 9);
                canvas.line("Date: " + DATE_FORMAT.format(record.getReleaseDate()), fonts.english(), 9);
                canvas.gap(6);

                canvas.bilingualTitle("RELEASE ORDER", "रिलीज ऑर्डर");
                canvas.gap(4);

                String sessionLabel = record.getReleaseSession() != null && record.getReleaseSession().name().equals("FORENOON")
                        ? "Forenoon (FN)" : "Afternoon (AN)";
                String text = "In pursuance of Office Order Ref. No. " + record.getOrder().getOrderRefNo() + " dated "
                        + DATE_FORMAT.format(record.getOrder().getOrderDate()) + ", " + record.getEmployee().getFullName()
                        + " is hereby released w.e.f. " + DATE_FORMAT.format(record.getReleaseDate()) + " (" + sessionLabel
                        + ") with advice to join at the place of posting.";
                canvas.bilingualParagraph(text, null, 10);

                canvas.table(
                        List.of("Sl No", "Name & Emp ID", "Place of Posting"),
                        List.of(List.of("1",
                                record.getEmployee().getFullName() + " (" + record.getEmployee().getEmployeeCode() + ")",
                                record.getToOffice().getName())),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.12f, 0.48f, 0.4f));

                canvas.gap(6);
                canvas.bilingualParagraph("Upon joining, a report to this effect may be forwarded to Head Office.", null, 10);

                canvas.signatureBlock("Manager (HR)");
                canvas.gap(10);
                canvas.distributionList(PdfHeaderFooterHelper.DISTRIBUTION_RELEASE);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render Release Order PDF", e);
        }
    }
}
