package in.gov.jci.hrms.service.pdf;

import in.gov.jci.hrms.dto.EssSalarySlipDetailResponse;
import in.gov.jci.hrms.dto.PayrollHeadLineResponse;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.Canvas;
import in.gov.jci.hrms.service.pdf.PdfHeaderFooterHelper.PdfFonts;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** GET /api/v1/ess/salary-slips/{tranId}/download - a printable CPSE-style salary slip. */
@Component
public class SalarySlipPdfGenerator {

    private final PdfHeaderFooterHelper helper;

    public SalarySlipPdfGenerator(PdfHeaderFooterHelper helper) {
        this.helper = helper;
    }

    public byte[] generate(EssSalarySlipDetailResponse slip) {
        try (PDDocument document = new PDDocument()) {
            PdfFonts fonts = helper.loadFonts(document);
            try (Canvas canvas = helper.newCanvas(document, fonts)) {
                canvas.bilingualTitle("SALARY SLIP", "वेतन पर्ची");
                canvas.gap(4);

                String monthLabel = LocalDate.of(slip.year(), slip.month(), 1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + slip.year();
                canvas.line("Pay Period: " + monthLabel, fonts.english(), 9.5f);
                canvas.line("Employee: " + slip.employeeName() + "  (Code: " + slip.empCode() + ")", fonts.english(), 9.5f);
                if (slip.designation() != null) {
                    canvas.line("Designation: " + slip.designation(), fonts.english(), 9.5f);
                }
                canvas.gap(6);

                List<PayrollHeadLineResponse> earnings = slip.headLines().stream().filter(h -> "EARNING".equals(h.category())).toList();
                List<PayrollHeadLineResponse> deductions = slip.headLines().stream().filter(h -> "DEDUCTION".equals(h.category())).toList();

                canvas.boldLine("Earnings", 10);
                canvas.table(List.of("Head", "Amount (Rs.)"),
                        earnings.stream().map(h -> List.of(h.description(), rupees(h.amount()))).toList(),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.7f, 0.3f));

                canvas.boldLine("Deductions", 10);
                canvas.table(List.of("Head", "Amount (Rs.)"),
                        deductions.stream().map(h -> List.of(h.description(), rupees(h.amount()))).toList(),
                        PdfHeaderFooterHelper.weightedColumns(canvas.contentWidth(), 0.7f, 0.3f));

                canvas.gap(6);
                canvas.boldLine("Gross Earnings: Rs. " + rupees(slip.grossAmount()), 10);
                canvas.boldLine("Total Deductions: Rs. " + rupees(slip.totalDeductions()), 10);
                canvas.boldLine("Net Pay: Rs. " + rupees(slip.netAmount()), 11);

                canvas.gap(10);
                canvas.line("This is a computer-generated salary slip and does not require a signature.", fonts.english(), 8);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render salary slip PDF", e);
        }
    }

    private String rupees(BigDecimal amount) {
        return amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : "0.00";
    }
}
