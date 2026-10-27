package com.mamitiana.efacture.supplier;

import java.time.Instant;
import java.util.List;

public record SupplierInvoiceResponse(
        Long id,
        SupplierInvoice.Status status,
        SupplierInvoice.Source source,
        String fileName,
        ExtractedInvoice data,
        List<InvoiceChecks.Issue> issues,
        Extraction extraction,
        String receivedBy,
        Instant receivedAt,
        String reviewedBy,
        Instant reviewedAt,
        String rejectionReason) {

    /** Which model read the document; null when the data came from the Factur-X XML or a person. */
    public record Extraction(String provider, String model, Long durationMs) {
    }

    static SupplierInvoiceResponse from(SupplierInvoice s) {
        return new SupplierInvoiceResponse(s.getId(), s.getStatus(), s.getSource(), s.getFileName(), s.data(),
                s.getIssues().stream().map(i -> new InvoiceChecks.Issue(i.getCode(), i.getSeverity(), i.getMessage())).toList(),
                s.getExtractionProvider() == null ? null
                        : new Extraction(s.getExtractionProvider(), s.getExtractionModel(), s.getExtractionMs()),
                s.getReceivedBy(), s.getReceivedAt(), s.getReviewedBy(), s.getReviewedAt(), s.getRejectionReason());
    }
}
