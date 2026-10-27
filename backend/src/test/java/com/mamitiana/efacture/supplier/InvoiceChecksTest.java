package com.mamitiana.efacture.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceChecksTest {

    private final InvoiceChecks checks = new InvoiceChecks();

    static ExtractedInvoice valid() {
        return new ExtractedInvoice("Imprimerie du Centre SARL", "404833048", "FR83404833048",
                "FR7630006000011234567890189", "IC-2026-0417", LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 11),
                "EUR",
                List.of(new ExtractedInvoice.Line("Flyers A5", BigDecimal.ONE, new BigDecimal("250.00"), new BigDecimal("20"), new BigDecimal("250.00")),
                        new ExtractedInvoice.Line("Cartes de visite", new BigDecimal("2"), new BigDecimal("45.00"), new BigDecimal("20"), new BigDecimal("90.00"))),
                new BigDecimal("340.00"), new BigDecimal("68.00"), new BigDecimal("408.00"));
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
        ExtractedInvoice v = valid();
        ExtractedInvoice misread = new ExtractedInvoice(v.supplierName(), "404833049", v.supplierVatNumber(),
                "FR7630006000011234567890188", v.invoiceNumber(), v.issueDate(), v.dueDate(), v.currency(), v.lines(),
                v.totalNet(), v.totalVat(), v.totalGross());

        assertThat(codes(checks.check(misread, false))).contains("INVALID_SIREN", "INVALID_IBAN");
    }

    @Test
    void catchesTotalsThatDoNotAddUp() {
        ExtractedInvoice v = valid();
        ExtractedInvoice wrong = new ExtractedInvoice(v.supplierName(), v.supplierSiren(), v.supplierVatNumber(),
                v.supplierIban(), v.invoiceNumber(), v.issueDate(), v.dueDate(), v.currency(), v.lines(),
                new BigDecimal("350.00"), new BigDecimal("68.00"), new BigDecimal("408.00"));

        assertThat(codes(checks.check(wrong, false))).contains("TOTALS_MISMATCH", "LINES_MISMATCH");
    }

    @Test
    void toleratesOneCentOfRounding() {
        ExtractedInvoice v = valid();
        ExtractedInvoice rounded = new ExtractedInvoice(v.supplierName(), v.supplierSiren(), v.supplierVatNumber(),
                v.supplierIban(), v.invoiceNumber(), v.issueDate(), v.dueDate(), v.currency(), v.lines(),
                v.totalNet(), new BigDecimal("68.01"), v.totalGross());

        assertThat(checks.check(rounded, false)).isEmpty();
    }

    @Test
    void flagsMissingFieldsAsErrorsAndMissingSirenAsWarning() {
        ExtractedInvoice empty = new ExtractedInvoice(null, null, null, null, null, null, null, null, List.of(), null, null, null);

        List<InvoiceChecks.Issue> issues = checks.check(empty, false);

        assertThat(issues).filteredOn(i -> i.severity() == InvoiceChecks.Severity.ERROR)
                .extracting(InvoiceChecks.Issue::code).containsOnly("MISSING_FIELD").hasSize(4);
        assertThat(codes(issues)).contains("MISSING_SIREN", "MISSING_IBAN");
    }

    @Test
    void flagsDuplicatesAndInconsistentDates() {
        ExtractedInvoice v = valid();
        ExtractedInvoice backwards = new ExtractedInvoice(v.supplierName(), v.supplierSiren(), v.supplierVatNumber(),
                v.supplierIban(), v.invoiceNumber(), v.issueDate(), v.issueDate().minusDays(1), v.currency(), v.lines(),
                v.totalNet(), v.totalVat(), v.totalGross());

        assertThat(codes(checks.check(backwards, true))).containsExactlyInAnyOrder("DUE_BEFORE_ISSUE", "DUPLICATE");
    }
}
