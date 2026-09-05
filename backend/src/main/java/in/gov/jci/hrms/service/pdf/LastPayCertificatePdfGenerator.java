package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.entity.EmployeeMovementRecord;
import in.gov.jci.hrms.entity.MovementLpcRecord;
import in.gov.jci.hrms.entity.SessionType;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.Canvas;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.PdfFonts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class LastPayCertificatePdfGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PdfHeaderFooterHelper helper;

    public LastPayCertificatePdfGenerator(PdfHeaderFooterHelper helper) {
        this.helper = helper;
    }

    public byte[] generate(MovementLpcRecord lpc) {
        EmployeeMovementRecord movement = lpc.getMovement();
        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = helper.loadFonts(document);
            try (Canvas canvas = helper.newCanvas(document, fonts)) {
                canvas.line("LPC No.: " + lpc.getLpcNumber(), fonts.english(), 9);
                canvas.line("Date: " + DATE_FORMAT.format(lpc.getGeneratedAt().atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate()),
                        fonts.english(), 9);
                canvas.gap(6);

                canvas.bilingualTitle("LAST PAY CERTIFICATE", "अंतिम वेतन प्रमाण पत्र");
                canvas.gap(6);

                canvas.boldLine("1. Employee Details", 10);
                canvas.line("Name: " + lpc.getEmployee().getFullName() + "  (Emp. Code: " + lpc.getEmployee().getEmployeeCode() + ")",
                        fonts.english(), 9.5f);
                canvas.line("Released From: " + movement.getFromOffice().getName() + "   Posted To: " + movement.getToOffice().getName(),
                        fonts.english(), 9.5f);
                canvas.line("Release Order Ref: " + nullSafe(movement.getReleaseOrderRef()) + "   Release Date: "
                        + (movement.getReleaseDate() != null ? DATE_FORMAT.format(movement.getReleaseDate()) : "—"), fonts.english(), 9.5f);
                if (movement.getJoiningReportNo() != null) {
                    canvas.line("Joining Report No.: " + movement.getJoiningReportNo(), fonts.english(), 9.5f);
                }
                canvas.gap(6);

                canvas.boldLine("2. Pay & Allowance Drawn (up to Release)", 10);
                canvas.table(
                        List.of("Basic Pay", "DA", "HRA", "Special Allowance", "Drawn Upto"),
                        List.of(List.of(
                                rupees(lpc.getRateOfPayBasic()), rupees(lpc.getRateOfDa()), rupees(lpc.getRateOfHra()),
                                rupees(lpc.getRateOfSpecialAllowance()),
                                DATE_FORMAT.format(lpc.getPayDrawnUptoDate()) + " (" + sessionLabel(lpc.getPayDrawnSession()) + ")")),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.2f, 0.2f, 0.2f, 0.2f, 0.2f));

                canvas.boldLine("3. Deductions & Recoveries", 10);
                canvas.table(
                        List.of("CPF Subscription (Monthly)", "CPF Advance (Outstanding)", "Festival Advance (Outstanding)"),
                        List.of(List.of(rupees(lpc.getCpfSubscription()), rupees(lpc.getCpfAdvanceBalance()), rupees(lpc.getFestivalAdvanceBalance()))),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.34f, 0.33f, 0.33f));

                canvas.boldLine("4. Certified Leave at Credit", 10);
                canvas.table(
                        List.of("Earned Leave (EL)", "Half Pay Leave (HPL)"),
                        List.of(List.of(lpc.getElBalanceDays() + " Days", lpc.getHplBalanceDays() + " Days")),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.5f, 0.5f));

                canvas.gap(6);
                canvas.wrapped("Certified that no further pay and allowances will be disbursed to the above-named employee "
                        + "by this releasing unit after the date noted above.", fonts.english(), 9.5f);

                canvas.dualSignatureBlock("DDO / Releasing Unit", "Accounts Officer");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render Last Pay Certificate PDF", e);
        }
    }

    private String sessionLabel(SessionType session) {
        return session == SessionType.FORENOON ? "FN" : "AN";
    }

    private String rupees(java.math.BigDecimal amount) {
        return amount != null ? "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP) : "—";
    }

    private String nullSafe(String value) {
        return value != null ? value : "—";
    }
}
