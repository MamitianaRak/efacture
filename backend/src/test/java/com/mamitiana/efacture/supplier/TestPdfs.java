package com.mamitiana.efacture.supplier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Plain PDF invoices (no Factur-X XML), as many small suppliers still send them. */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] plainInvoice(List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(16);
                content.newLineAtOffset(50, 740);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] sampleInvoice() {
        return plainInvoice(List.of(
                "IMPRIMERIE DU CENTRE SARL",
                "12 rue des Arts, 77000 Melun",
                "SIREN 404 833 048 - TVA FR83404833048",
                "FACTURE N° IC-2026-0417 du 12/10/2026",
                "Echeance : 11/11/2026",
                "Flyers A5 (x1000)        1    250,00    20 %    250,00",
                "Cartes de visite (x500)  2     45,00    20 %     90,00",
                "Total HT 340,00   TVA 68,00   Total TTC 408,00",
                "IBAN FR76 3000 6000 0112 3456 7890 189"));
    }
}
