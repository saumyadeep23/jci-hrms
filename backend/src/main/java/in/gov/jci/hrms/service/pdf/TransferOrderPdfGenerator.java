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
import java.util.List;

/** Ref pattern "JCI/Pers./HO/Transfer/{FY}/{Seq}" is whatever MovementOrder.orderRefNo was entered as, not regenerated here. */
@Component
public class TransferOrderPdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final String BENEFIT_ADMISSIBLE_EN = "They shall be in receipt of transfer benefits as per the extant rules of the Corporation.";
    private static final String BENEFIT_ADMISSIBLE_HI = "उन्हें कॉर्पोरेशन के नियमों के अनुसार ट्रांसफर बेनिफिट्स मिलेंगे।";
    private static final String BENEFIT_NOT_ADMISSIBLE_EN =
            "Since this transfer is made on the employee's own request, no transfer benefits (Transfer TA/DA, CTG, etc.) shall be admissible.";
    private static final String BENEFIT_NOT_ADMISSIBLE_HI =
            "चूंकि यह स्थानांतरण कर्मचारी के स्वयं के अनुरोध पर किया गया है, अतः कोई स्थानांतरण लाभ देय नहीं होगा।";

    private final PdfHeaderFooterHelper helper;

    public TransferOrderPdfGenerator(PdfHeaderFooterHelper helper) {
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

                canvas.bilingualParagraph(
                        "Consequent to the assent of the Competent Authority, the following transfer is being undertaken -", null, 10);

                canvas.table(
                        List.of("Sl. No.", "Name (Emp. No.)", "Designation", "Present Posting (From)", "Transferred To"),
                        tableRows(records),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.08f, 0.27f, 0.18f, 0.235f, 0.235f));

                canvas.gap(6);
                boolean allBenefitAdmissible = records.stream().allMatch(EmployeeMovementRecord::isTransferBenefitAdmissible);
                if (allBenefitAdmissible) {
                    canvas.bilingualParagraph(BENEFIT_ADMISSIBLE_EN, BENEFIT_ADMISSIBLE_HI, 10);
                } else {
                    canvas.bilingualParagraph(BENEFIT_NOT_ADMISSIBLE_EN, BENEFIT_NOT_ADMISSIBLE_HI, 10);
                }

                canvas.signatureBlock("Manager (HR)");
                canvas.gap(10);
                canvas.distributionList(PdfHeaderFooterHelper.DISTRIBUTION_STANDARD);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render Transfer Order PDF", e);
        }
    }

    private List<List<String>> tableRows(List<EmployeeMovementRecord> records) {
        List<List<String>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            EmployeeMovementRecord r = records.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    r.getEmployee().getFullName() + " (" + r.getEmployee().getEmployeeCode() + ")",
                    r.getFromDesignation().getTitle(),
                    r.getFromOffice().getName(),
                    r.getToOffice().getName()));
        }
        return rows;
    }
}
