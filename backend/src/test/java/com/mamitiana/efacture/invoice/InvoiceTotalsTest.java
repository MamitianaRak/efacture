package com.mamitiana.efacture.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTotalsTest {

    private static InvoiceLine line(String quantity, String unitPrice, String vatRate) {
        return new InvoiceLine("item", new BigDecimal(quantity), new BigDecimal(unitPrice), new BigDecimal(vatRate));
    }

    @Test
    void sumsLinesAndGroupsVatByRate() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(
                line("2", "100.00", "20"),
                line("1", "50.00", "20"),
                line("3", "10.00", "5.5")));

        assertThat(totals.totalNet()).isEqualByComparingTo("280.00");
        assertThat(totals.totalVat()).isEqualByComparingTo("51.65");
        assertThat(totals.totalGross()).isEqualByComparingTo("331.65");
        assertThat(totals.vatBreakdown()).hasSize(2);
        assertThat(totals.vatBreakdown().get(1).rate()).isEqualByComparingTo("20");
        assertThat(totals.vatBreakdown().get(1).taxableAmount()).isEqualByComparingTo("250.00");
        assertThat(totals.vatBreakdown().get(1).vatAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void computesVatOnTheRateTotalNotLineByLine() {
        // Three lines of 0.10 at 5.5 %: line by line each VAT rounds to 0.01 (0.03 in total),
        // on the rate total it is 0.30 * 5.5 % = 0.0165, rounded to 0.02.
        InvoiceTotals totals = InvoiceTotals.of(List.of(
                line("1", "0.10", "5.5"),
                line("1", "0.10", "5.5"),
                line("1", "0.10", "5.5")));

        assertThat(totals.totalVat()).isEqualByComparingTo("0.02");
    }

    @Test
    void roundsLineAmountToTheCent() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(line("1.333", "10.00", "0")));

        assertThat(totals.totalNet()).isEqualByComparingTo("13.33");
        assertThat(totals.totalGross()).isEqualByComparingTo("13.33");
    }

    @Test
    void treatsEquivalentRatesAsOneRate() {
        InvoiceTotals totals = InvoiceTotals.of(List.of(line("1", "10.00", "20"), line("1", "10.00", "20.00")));

        assertThat(totals.vatBreakdown()).hasSize(1);
    }

    @Test
    void emptyInvoiceIsZero() {
        InvoiceTotals totals = InvoiceTotals.of(List.of());

        assertThat(totals.totalGross()).isEqualByComparingTo("0.00");
        assertThat(totals.vatBreakdown()).isEmpty();
    }
}
