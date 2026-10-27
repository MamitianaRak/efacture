package com.mamitiana.efacture.facturx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamitiana.efacture.invoice.Invoice;
import com.mamitiana.efacture.invoice.InvoiceFixtures;
import com.mamitiana.efacture.invoice.InvoiceLine;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.validator.ZUGFeRDValidator;

/** Generates real Factur-X files and checks them with the Mustang validator (PDF/A-3 + EN 16931 rules). */
class FacturXGeneratorTest {

    private final FacturXGenerator generator = new FacturXGenerator();

    private static void assertValid(byte[] pdf, String name) throws Exception {
        // Kept in target/ so the generated file can be opened and inspected.
        Files.write(Path.of("target", name), pdf);

        ZUGFeRDValidator validator = new ZUGFeRDValidator();
        String report = validator.validate(pdf, name);
        assertThat(validator.wasCompletelyValid()).as(report).isTrue();
    }

    @Test
    void invoiceIsValidFacturX() throws Exception {
        byte[] pdf = generator.generate(InvoiceFixtures.issuedInvoice(), InvoiceFixtures.seller());

        assertValid(pdf, "facturx-invoice.pdf");
    }

    @Test
    void embeddedXmlCarriesTheInvoiceData() throws Exception {
        Invoice invoice = InvoiceFixtures.issuedInvoice();
        byte[] pdf = generator.generate(invoice, InvoiceFixtures.seller());

        ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter(new java.io.ByteArrayInputStream(pdf));
        org.mustangproject.Invoice imported = importer.extractInvoice();

        assertThat(imported.getNumber()).isEqualTo("F-2026-00001");
        assertThat(imported.getSender().getName()).isEqualTo("Atelier Dupont SARL");
        assertThat(imported.getRecipient().getName()).isEqualTo("Boulangerie Martin");
        // The grand total written by Mustang matches the one our domain computed.
        assertThat(importer.getUTF8()).contains("GrandTotalAmount>" + invoice.getTotalGross().toPlainString() + "<");
    }

    @Test
    void creditNoteIsValidFacturXAndReferencesTheInvoice() throws Exception {
        Invoice creditNote = InvoiceFixtures.issuedCreditNote(InvoiceFixtures.issuedInvoice());
        byte[] pdf = generator.generate(creditNote, InvoiceFixtures.seller());

        assertValid(pdf, "facturx-credit-note.pdf");
        String xml = new ZUGFeRDInvoiceImporter(new java.io.ByteArrayInputStream(pdf)).getUTF8();
        assertThat(xml).contains(">381<").contains("F-2026-00001");
    }

    @Test
    void zeroRatedLineIsValid() throws Exception {
        Invoice invoice = InvoiceFixtures.issuedInvoice(List.of(
                new InvoiceLine("Prestation exonérée", BigDecimal.ONE, new BigDecimal("20.00"), BigDecimal.ZERO)));

        assertValid(generator.generate(invoice, InvoiceFixtures.seller()), "facturx-zero-rate.pdf");
    }

    @Test
    void draftCannotBeExported() {
        Invoice draft = Invoice.draft(InvoiceFixtures.customer(), InvoiceFixtures.TODAY, List.of());

        assertThatThrownBy(() -> generator.generate(draft, InvoiceFixtures.seller()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
