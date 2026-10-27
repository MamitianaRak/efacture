package com.mamitiana.efacture.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InvoiceResponse(
        Long id,
        InvoiceType type,
        InvoiceStatus status,
        String number,
        Long customerId,
        String customerName,
        String originalInvoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        List<Line> lines,
        List<InvoiceTotals.VatBreakdown> vatBreakdown,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross,
        long version) {

    public record Line(int position, String description, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal vatRate, BigDecimal netAmount) {
    }

    static InvoiceResponse from(Invoice invoice) {
        InvoiceTotals totals = invoice.totals();
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getType(),
                invoice.getStatus(),
                invoice.getNumber(),
                invoice.getCustomer().getId(),
                invoice.getCustomer().getLegalName(),
                invoice.getOriginalInvoice() == null ? null : invoice.getOriginalInvoice().getNumber(),
                invoice.getIssueDate(),
                invoice.getDueDate(),
                invoice.getCurrency(),
                invoice.getLines().stream()
                        .map(l -> new Line(l.getPosition(), l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                                l.getVatRate(), l.netAmount()))
                        .toList(),
                totals.vatBreakdown(),
                totals.totalNet(),
                totals.totalVat(),
                totals.totalGross(),
                invoice.getVersion());
    }
}
