package com.mamitiana.efacture.supplier.eval;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/** Draws simple invoice pages with PDFBox, and turns a PDF into a "scanned" image for the evaluation set. */
final class InvoicePdfWriter implements AutoCloseable {

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final PDDocument document = new PDDocument();
    private PDPageContentStream content;

    InvoicePdfWriter() {
        newPage();
    }

    InvoicePdfWriter newPage() {
        try {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InvoicePdfWriter text(float x, float y, float size, String text) {
        return write(REGULAR, x, y, size, text);
    }

    InvoicePdfWriter bold(float x, float y, float size, String text) {
        return write(BOLD, x, y, size, text);
    }

    /** Right-aligned text ending at x, as amounts are printed in invoice tables. */
    InvoicePdfWriter right(float x, float y, float size, String text) {
        try {
            float width = REGULAR.getStringWidth(text) / 1000 * size;
            return write(REGULAR, x - width, y, size, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    InvoicePdfWriter line(float x1, float y1, float x2, float y2) {
        try {
            content.moveTo(x1, y1);
            content.lineTo(x2, y2);
            content.stroke();
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InvoicePdfWriter write(PDType1Font font, float x, float y, float size, String text) {
        try {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, y);
            content.showText(text);
            content.endText();
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    byte[] toPdf() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            content.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        document.close();
    }

    /**
     * A phone scan of the first page: grey, slightly rotated, with noise, and no text layer at all.
     * The seed keeps the dataset identical from one run to the next.
     */
    static byte[] scanOf(byte[] pdf, long seed) {
        try (PDDocument source = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage page = new PDFRenderer(source).renderImageWithDPI(0, 110, ImageType.GRAY);
            BufferedImage scan = new BufferedImage(page.getWidth(), page.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scan.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(new Color(236, 233, 226));
            g.fillRect(0, 0, scan.getWidth(), scan.getHeight());
            g.rotate(Math.toRadians(1.3), scan.getWidth() / 2.0, scan.getHeight() / 2.0);
            g.drawImage(page, 0, 0, null);
            g.dispose();

            Random random = new Random(seed);
            for (int i = 0; i < scan.getWidth() * scan.getHeight() / 40; i++) {
                int x = random.nextInt(scan.getWidth());
                int y = random.nextInt(scan.getHeight());
                int grey = 150 + random.nextInt(80);
                scan.setRGB(x, y, new Color(grey, grey, grey).getRGB());
            }
            ImageIO.write(scan, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
