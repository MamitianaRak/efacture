package com.mamitiana.efacture.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/** List item: no lines, so listing does not load every line of every invoice. */
public record InvoiceSummary(
        Long id,
        InvoiceType type,
        InvoiceStatus status,
        String number,
        String customerName,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal totalGross) {

    static InvoiceSummary from(Invoice invoice) {
        return new InvoiceSummary(invoice.getId(), invoice.getType(), invoice.getStatus(), invoice.getNumber(),
                invoice.getCustomer().getLegalName(), invoice.getIssueDate(), invoice.getDueDate(),
                invoice.getTotalGross());
    }
}
