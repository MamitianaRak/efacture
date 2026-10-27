package com.mamitiana.efacture.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.customer.Customer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private static List<InvoiceLine> lines() {
        return new ArrayList<>(List.of(
                new InvoiceLine("Conseil", new BigDecimal("2"), new BigDecimal("500.00"), new BigDecimal("20"))));
    }

    private static Invoice draft() {
        return Invoice.draft(new Customer(), TODAY.plusDays(30), lines());
    }

    @Test
    void draftHasNoNumberAndComputedTotals() {
        Invoice invoice = draft();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(invoice.getNumber()).isNull();
        assertThat(invoice.getTotalGross()).isEqualByComparingTo("1200.00");
        assertThat(invoice.getLines()).extracting(InvoiceLine::getPosition).containsExactly(1);
    }

    @Test
    void issuingFreezesTheInvoice() {
        Invoice invoice = draft();
        invoice.issue("F-2026-00001", TODAY, Instant.now());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThatThrownBy(() -> invoice.updateDraft(new Customer(), TODAY, lines()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("F-2026-00001");
        assertThatThrownBy(() -> invoice.issue("F-2026-00002", TODAY, Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creditNoteCopiesTheIssuedInvoice() {
        Invoice invoice = draft();
        invoice.issue("F-2026-00001", TODAY, Instant.now());

        Invoice creditNote = Invoice.creditNoteFor(invoice);

        assertThat(creditNote.getType()).isEqualTo(InvoiceType.CREDIT_NOTE);
        assertThat(creditNote.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(creditNote.getOriginalInvoice()).isSameAs(invoice);
        assertThat(creditNote.getTotalGross()).isEqualByComparingTo(invoice.getTotalGross());
        assertThat(creditNote.getLines()).isNotSameAs(invoice.getLines())
                .allSatisfy(line -> assertThat(line.getInvoice()).isSameAs(creditNote));
    }

    @Test
    void creditNoteRequiresAnIssuedInvoice() {
        assertThatThrownBy(() -> Invoice.creditNoteFor(draft())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creditNoteCannotItselfBeCredited() {
        Invoice invoice = draft();
        invoice.issue("F-2026-00001", TODAY, Instant.now());
        Invoice creditNote = Invoice.creditNoteFor(invoice);
        creditNote.issue("AV-2026-00001", TODAY, Instant.now());

        assertThatThrownBy(() -> Invoice.creditNoteFor(creditNote)).isInstanceOf(BusinessRuleException.class);
    }
}
