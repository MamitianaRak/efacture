package com.mamitiana.efacture.supplier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SupplierInvoiceSummary(
        Long id,
        SupplierInvoice.Status status,
        SupplierInvoice.Source source,
        String supplierName,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        BigDecimal totalGross,
        long errorCount,
        Instant receivedAt) {

    static SupplierInvoiceSummary from(SupplierInvoice s) {
        long errors = s.getIssues().stream().filter(i -> i.getSeverity() == InvoiceChecks.Severity.ERROR).count();
        return new SupplierInvoiceSummary(s.getId(), s.getStatus(), s.getSource(), s.getSupplierName(),
                s.getInvoiceNumber(), s.getIssueDate(), s.getDueDate(), s.getTotalGross(), errors, s.getReceivedAt());
    }
}
