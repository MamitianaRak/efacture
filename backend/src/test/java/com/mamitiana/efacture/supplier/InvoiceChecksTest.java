package com.mamitiana.efacture.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamitiana.efacture.supplier.ExtractedInvoice.PaymentMeans;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceChecksTest {

    private final InvoiceChecks checks = new InvoiceChecks();

    static ExtractedInvoice valid() {
        return new ExtractedInvoice("Imprimerie du Centre SARL", "404833048", "FR83404833048",
                "FR7630006000011234567890189", PaymentMeans.TRANSFER, "IC-2026-0417",
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 11), "EUR",
                List.of(new ExtractedInvoice.Line("Flyers A5", BigDecimal.ONE, new BigDecimal("250.00"), new BigDecimal("20"), new BigDecimal("250.00")),
                        new ExtractedInvoice.Line("Cartes de visite", new BigDecimal("2"), new BigDecimal("45.00"), new BigDecimal("20"), new BigDecimal("90.00"))),
                new BigDecimal("340.00"), new BigDecimal("68.00"), new BigDecimal("408.00"));
    }

    /** Copy of {@link #valid()} with some fields changed. */
    static ExtractedInvoice with(String siren, String iban, PaymentMeans means, LocalDate due,
                                 BigDecimal net, BigDecimal vat) {
        ExtractedInvoice v = valid();
        return new ExtractedInvoice(v.supplierName(), siren, v.supplierVatNumber(), iban, means, v.invoiceNumber(),
                v.issueDate(), due, v.currency(), v.lines(), net, vat, v.totalGross());
    }

    private static List<String> codes(List<InvoiceChecks.Issue> issues) {
        return issues.stream().map(InvoiceChecks.Issue::code).toList();
    }

    @Test
    void consistentInvoicePassesEveryCheck() {
        assertThat(checks.check(valid(), false)).isEmpty();
    }

    @Test
    void catchesASirenOrIbanMisreadByOneDigit() {
        ExtractedInvoice misread = with("404833049", "FR7630006000011234567890188", PaymentMeans.TRANSFER,
                valid().dueDate(), valid().totalNet(), valid().totalVat());

        assertThat(codes(checks.check(misread, false))).contains("INVALID_SIREN", "INVALID_IBAN");
    }

    @Test
    void catchesTotalsThatDoNotAddUp() {
        ExtractedInvoice wrong = with("404833048", valid().supplierIban(), PaymentMeans.TRANSFER, valid().dueDate(),
                new BigDecimal("350.00"), new BigDecimal("68.00"));

        assertThat(codes(checks.check(wrong, false))).contains("TOTALS_MISMATCH", "LINES_MISMATCH");
    }

    @Test
    void toleratesOneCentOfRounding() {
        ExtractedInvoice rounded = with("404833048", valid().supplierIban(), PaymentMeans.TRANSFER, valid().dueDate(),
                valid().totalNet(), new BigDecimal("68.01"));

        assertThat(checks.check(rounded, false)).isEmpty();
    }

    @Test
    void flagsMissingFieldsAsErrors() {
        List<InvoiceChecks.Issue> issues = checks.check(ExtractedInvoice.empty(), false);

        assertThat(issues).filteredOn(i -> i.severity() == InvoiceChecks.Severity.ERROR)
                .extracting(InvoiceChecks.Issue::code).containsOnly("MISSING_FIELD").hasSize(4);
        assertThat(codes(issues)).contains("MISSING_SIREN");
    }

    @Test
    void missingIbanMattersOnlyWhenATransferIsExpected() {
        LocalDate due = valid().dueDate();
        BigDecimal net = valid().totalNet();
        BigDecimal vat = valid().totalVat();

        assertThat(codes(checks.check(with("404833048", null, PaymentMeans.TRANSFER, due, net, vat), false)))
                .containsExactly("MISSING_IBAN");
        for (PaymentMeans means : List.of(PaymentMeans.DIRECT_DEBIT, PaymentMeans.CARD, PaymentMeans.ALREADY_PAID, PaymentMeans.UNKNOWN)) {
            assertThat(checks.check(with("404833048", null, means, due, net, vat), false)).as(means.name()).isEmpty();
        }
    }

    @Test
    void warnsThatTheIbanOnADirectDebitInvoiceIsUsuallyTheBuyers() {
        ExtractedInvoice debit = with("404833048", valid().supplierIban(), PaymentMeans.DIRECT_DEBIT, valid().dueDate(),
                valid().totalNet(), valid().totalVat());

        assertThat(codes(checks.check(debit, false))).containsExactly("IBAN_ON_DIRECT_DEBIT");
    }

    @Test
    void flagsDuplicatesAndInconsistentDates() {
        ExtractedInvoice backwards = with("404833048", valid().supplierIban(), PaymentMeans.TRANSFER,
                valid().issueDate().minusDays(1), valid().totalNet(), valid().totalVat());

        assertThat(codes(checks.check(backwards, true))).containsExactlyInAnyOrder("DUE_BEFORE_ISSUE", "DUPLICATE");
    }

    // Text layer of a real invoice that has no IBAN (SIRET only, no SIREN printed on its own).
    private static final String DOCUMENT_WITHOUT_IBAN = """
            FACTURE N°: IC-2026-0417   Imprimerie du Centre SARL
            Siret: 40483304800012 - TVA: FR83 404 833 048
            Total HT 340,00  TVA 68,00  Total TTC 408,00
            """;

    @Test
    void catchesAnIbanInventedByTheModel() {
        // Real case: the model filled the IBAN field for an invoice that had none.
        List<InvoiceChecks.Issue> issues = checks.check(valid(), false,
                new InvoiceChecks.Grounding(DOCUMENT_WITHOUT_IBAN, true));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("IBAN_NOT_IN_DOCUMENT");
            assertThat(issue.severity()).isEqualTo(InvoiceChecks.Severity.ERROR);
            assertThat(issue.message()).contains("inventé");
        });
    }

    @Test
    void findsTheSirenInsideTheSiretAndIgnoresSpacing() {
        String withIban = DOCUMENT_WITHOUT_IBAN + "IBAN : FR76 3000 6000 0112 3456 7890 189";

        assertThat(checks.check(valid(), false, new InvoiceChecks.Grounding(withIban, true))).isEmpty();
    }

    @Test
    void anIbanTypedByAPersonAndAbsentFromTheDocumentIsAWarning() {
        // After a correction, the value is the person's: worth a check with the supplier, not a model error.
        List<InvoiceChecks.Issue> issues = checks.check(valid(), false,
                new InvoiceChecks.Grounding(DOCUMENT_WITHOUT_IBAN, false));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(InvoiceChecks.Severity.WARNING);
            assertThat(issue.message()).contains("canal déjà connu");
        });
    }

    @Test
    void scansWithoutTextLayerSkipGrounding() {
        assertThat(checks.check(valid(), false, new InvoiceChecks.Grounding("", true))).isEmpty();
    }
}
