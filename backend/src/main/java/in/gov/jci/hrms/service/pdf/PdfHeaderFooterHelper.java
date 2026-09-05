package in.gov.jci.hrms.service.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared JCI Patsan Bhavan letterhead, bilingual (Hindi/English) text layout, and simple table
 * drawing for the movement-order PDFs (Transfer/Promotion/Release/LPC) - via Apache PDFBox, the
 * same library PdfExportService already uses for the reporting hub's export button.
 *
 * <p>Devanagari rendering: PDFBox's Base-14 fonts (Helvetica etc.) are WinAnsi-encoded and have no
 * Devanagari glyphs at all - showText() with a Devanagari codepoint on one of those throws, it
 * doesn't just render blank. A real Unicode TTF with Devanagari coverage is required, loaded as a
 * PDType0Font. This deliberately never bundles a font file in the repository: Windows-bundled
 * fonts (e.g. Nirmala UI, the only Devanagari font found on this dev machine's C:\Windows\Fonts)
 * are proprietary and not redistributable, and no verified download source was available to fetch
 * an open-licensed one during development. Instead, findDevanagariFont() looks for one already
 * installed on the runtime filesystem (the Dockerfile installs the small, OFL-licensed
 * fonts-lohit-deva Debian package for exactly this) or on the classpath, and getHindiFont()
 * degrades gracefully - never throwing - to a bracketed English fallback note when none is found,
 * so PDF generation always succeeds even before that font layer exists.
 */
@Component
public class PdfHeaderFooterHelper {

    private static final Logger log = LoggerFactory.getLogger(PdfHeaderFooterHelper.class);

    private static final List<String> CLASSPATH_FONT_CANDIDATES = List.of(
            "fonts/NotoSansDevanagari-Regular.ttf", "fonts/Lohit-Devanagari.ttf");
    private static final List<String> FILESYSTEM_FONT_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/lohit-devanagari/Lohit-Devanagari.ttf",
            "/usr/share/fonts/truetype/noto/NotoSansDevanagari-Regular.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansDevanagari-Regular.otf");

    public static final PDRectangle A4 = PDRectangle.A4;
    public static final float MARGIN = 42f;

    /** JCI specimen distribution list (Transfer/Promotion Orders). */
    public static final List<String> DISTRIBUTION_STANDARD = List.of(
            "MD's Secretariat", "DF's Secretariat", "CVO", "GM (O/M)", "DGM (Finance)", "Chief Managers",
            "Sr. Managers", "Managers", "Dy. Managers", "Asst. Managers", "All RO/RLDs", "Person Concerned",
            "Guard File/Website");

    /** JCI specimen distribution list for a Release Order - adds the IT Dept line. */
    public static final List<String> DISTRIBUTION_RELEASE = concat(DISTRIBUTION_STANDARD,
            "IT Dept (For information, updation in e-Office, website & wherever applicable)");

    private static List<String> concat(List<String> base, String extra) {
        List<String> combined = new ArrayList<>(base);
        combined.add(extra);
        return List.copyOf(combined);
    }

    public record PdfFonts(PDFont english, PDFont englishBold, PDFont devanagari) {
        /** Whether Hindi text can actually be rendered, vs. falling back to a bracketed note. */
        public boolean hasDevanagari() {
            return devanagari != null;
        }
    }

    public PdfFonts loadFonts(PDDocument document) {
        PDFont english = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDFont englishBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont devanagari = loadDevanagariFont(document);
        return new PdfFonts(english, englishBold, devanagari);
    }

    private PDFont loadDevanagariFont(PDDocument document) {
        for (String classpathPath : CLASSPATH_FONT_CANDIDATES) {
            try (InputStream in = new ClassPathResource(classpathPath).getInputStream()) {
                return PDType0Font.load(document, in, true);
            } catch (IOException ignored) {
                // Not present on the classpath - try the next candidate.
            }
        }
        for (String path : FILESYSTEM_FONT_CANDIDATES) {
            Path file = Path.of(path);
            if (Files.isReadable(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    return PDType0Font.load(document, in, true);
                } catch (IOException e) {
                    log.warn("Found Devanagari font candidate at {} but could not load it: {}", path, e.getMessage());
                }
            }
        }
        log.warn("No Devanagari-capable font found (checked classpath fonts/ and {}) - bilingual PDFs will render "
                + "Hindi lines as a bracketed English fallback note instead. Install fonts-lohit-deva (see Dockerfile) to fix.",
                FILESYSTEM_FONT_CANDIDATES);
        return null;
    }

    /** Never throws: used for a Devanagari-scripted line when fonts.devanagari() may be null. */
    public String hindiOrFallback(PdfFonts fonts, String hindiText) {
        return fonts.hasDevanagari() ? hindiText : "[Hindi text omitted - Devanagari font not installed on this server]";
    }

    public PDFont hindiFont(PdfFonts fonts) {
        return fonts.hasDevanagari() ? fonts.devanagari() : fonts.english();
    }

    /**
     * A mutable cursor over an open PDDocument that paginates automatically (a new page repeats
     * the letterhead) - all four movement-order PDF generators build their document through this,
     * so the letterhead/footer/table/word-wrap logic is written exactly once.
     */
    public final class Canvas implements AutoCloseable {
        private final PDDocument document;
        private final PdfFonts fonts;
        private final float contentTop;
        private final float contentBottom;
        private final float pageWidth;
        private PDPageContentStream cs;
        private float y;

        private Canvas(PDDocument document, PdfFonts fonts) {
            this.document = document;
            this.fonts = fonts;
            this.pageWidth = A4.getWidth();
            this.contentBottom = MARGIN + 16f;
            this.contentTop = A4.getHeight() - MARGIN;
            newPage();
        }

        public float contentWidth() {
            return pageWidth - 2 * MARGIN;
        }

        public float y() {
            return y;
        }

        public void newPage() {
            closeContentStream();
            PDPage page = new PDPage(A4);
            document.addPage(page);
            try {
                cs = new PDPageContentStream(document, page);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            y = contentTop;
            drawLetterhead();
        }

        private void drawLetterhead() {
            centerLine("THE JUTE CORPORATION OF INDIA LIMITED", fonts.englishBold(), 13);
            centerLine("(A Government of India Enterprise)", fonts.english(), 9);
            centerLineHindi("भारतीय पटसन निगम लिमिटेड (भारत सरकार का उपक्रम)", 10);
            centerLine("Patsan Bhavan, 3rd & 4th Floor, Block-CF, Action Area 1, New Town, Kolkata - 700156", fonts.english(), 8);
            centerLine("CIN: U17232WB1971GOI027958  |  Website: www.jutecorp.in  |  Email: jci@jcimail.in", fonts.english(), 8);
            hRule();
            gap(6);
        }

        public void ensureSpace(float needed) {
            if (y - needed < contentBottom) {
                newPage();
            }
        }

        public void gap(float amount) {
            y -= amount;
        }

        public void hRule() {
            try {
                cs.setLineWidth(0.75f);
                cs.moveTo(MARGIN, y);
                cs.lineTo(pageWidth - MARGIN, y);
                cs.stroke();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            y -= 4;
        }

        public void line(String text, PDFont font, float size) {
            ensureSpace(size + 4);
            writeAt(MARGIN, text, font, size);
            y -= size + 4;
        }

        public void boldLine(String text, float size) {
            line(text, fonts.englishBold(), size);
        }

        public void centerLine(String text, PDFont font, float size) {
            ensureSpace(size + 3);
            float width = stringWidth(text, font, size);
            writeAt((pageWidth - width) / 2, text, font, size);
            y -= size + 3;
        }

        public void centerLineHindi(String hindiText, float size) {
            String text = hindiOrFallback(fonts, hindiText);
            centerLine(text, hindiFont(fonts), size);
        }

        /** English then Hindi (or its fallback) on consecutive lines, both centered - the "कार्यालय आदेश / OFFICE ORDER" title pattern. */
        public void bilingualTitle(String english, String hindi) {
            centerLineHindi(hindi, 12);
            centerLine(english, fonts.englishBold(), 12);
        }

        /** English paragraph (word-wrapped), then its Hindi translation (or fallback) word-wrapped underneath, when both are given. hindi may be null for English-only content. */
        public void bilingualParagraph(String english, String hindi, float size) {
            wrapped(english, fonts.english(), size);
            if (hindi != null) {
                gap(2);
                wrapped(hindiOrFallback(fonts, hindi), hindiFont(fonts), size);
            }
            gap(4);
        }

        public void wrapped(String text, PDFont font, float size) {
            for (String wrapLine : wrapLines(text, font, size, contentWidth())) {
                line(wrapLine, font, size);
            }
        }

        public List<String> wrapLines(String text, PDFont font, float size, float maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (stringWidth(candidate, font, size) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        public void signatureBlock(String designation) {
            ensureSpace(40);
            gap(24);
            line("Sd/-", fonts.english(), 10);
            boldLine(designation, 10);
        }

        /** Two side-by-side sign-off blocks (e.g. LPC's DDO / Accounts Officer) at the same y. */
        public void dualSignatureBlock(String leftLabel, String rightLabel) {
            ensureSpace(40);
            gap(24);
            float rightX = MARGIN + contentWidth() / 2;
            writeAt(MARGIN, "Sd/-", fonts.english(), 10);
            writeAt(rightX, "Sd/-", fonts.english(), 10);
            y -= 14;
            writeAt(MARGIN, leftLabel, fonts.englishBold(), 10);
            writeAt(rightX, rightLabel, fonts.englishBold(), 10);
            y -= 14;
        }

        public void distributionList(List<String> items) {
            ensureSpace(20);
            boldLine("Distribution:", 9);
            for (int i = 0; i < items.size(); i++) {
                line((i + 1) + ". " + items.get(i), fonts.english(), 8.5f);
            }
        }

        /** Simple bordered grid - equal-width columns unless colWidths is given. Header row is bold. */
        public void table(List<String> headers, List<List<String>> rows, float[] colWidths) {
            float rowHeight = 16f;
            float[] widths = colWidths != null ? colWidths : equalWidths(headers.size());

            ensureSpace(rowHeight);
            drawTableRow(headers, widths, fonts.englishBold(), 8, rowHeight, true);
            for (List<String> row : rows) {
                ensureSpace(rowHeight);
                drawTableRow(row, widths, fonts.english(), 8, rowHeight, false);
            }
            gap(4);
        }

        private float[] equalWidths(int columns) {
            float[] widths = new float[columns];
            float each = contentWidth() / columns;
            java.util.Arrays.fill(widths, each);
            return widths;
        }

        private void drawTableRow(List<String> cells, float[] widths, PDFont font, float size, float rowHeight, boolean isHeader) {
            float rowTop = y;
            float cellX = MARGIN;
            try {
                if (isHeader) {
                    cs.setLineWidth(0.75f);
                }
                cs.moveTo(MARGIN, rowTop);
                cs.lineTo(MARGIN + sum(widths), rowTop);
                cs.stroke();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            for (int i = 0; i < cells.size() && i < widths.length; i++) {
                String value = cells.get(i);
                String truncated = truncateToWidth(value, font, size, widths[i] - 4);
                writeAt(cellX + 2, truncated, font, size);
                cellX += widths[i];
            }
            y -= rowHeight;
            try {
                cs.moveTo(MARGIN, y + (rowHeight - size));
                cs.lineTo(MARGIN + sum(widths), y + (rowHeight - size));
                cs.stroke();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        private String truncateToWidth(String text, PDFont font, float size, float maxWidth) {
            if (stringWidth(text, font, size) <= maxWidth) {
                return text;
            }
            String truncated = text;
            while (!truncated.isEmpty() && stringWidth(truncated + "...", font, size) > maxWidth) {
                truncated = truncated.substring(0, truncated.length() - 1);
            }
            return truncated + "...";
        }

        private float sum(float[] values) {
            float total = 0;
            for (float v : values) total += v;
            return total;
        }

        private void writeAt(float x, String text, PDFont font, float size) {
            try {
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(x, y);
                cs.showText(text);
                cs.endText();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        private float stringWidth(String text, PDFont font, float size) {
            try {
                return font.getStringWidth(text) / 1000 * size;
            } catch (IOException e) {
                // A character this font truly cannot encode - fall back to a conservative estimate rather than throw mid-layout.
                return text.length() * size * 0.6f;
            }
        }

        private void closeContentStream() {
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                cs = null;
            }
        }

        @Override
        public void close() {
            closeContentStream();
        }
    }

    public Canvas newCanvas(PDDocument document, PdfFonts fonts) {
        return new Canvas(document, fonts);
    }

    /** Column widths as fractions of totalWidth - e.g. weightedColumns(w, 0.1f, 0.3f, 0.6f). */
    public static float[] weightedColumns(float totalWidth, float... weights) {
        float[] widths = new float[weights.length];
        for (int i = 0; i < weights.length; i++) {
            widths[i] = totalWidth * weights[i];
        }
        return widths;
    }
}
