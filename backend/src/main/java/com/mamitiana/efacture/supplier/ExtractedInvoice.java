package com.mamitiana.efacture.supplier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What was read from a supplier invoice, whether from the Factur-X XML or by a model. This record is also the
 * JSON schema the model must fill: every field it cannot read on the document must stay null.
 */
public record ExtractedInvoice(
        String supplierName,
        String supplierSiren,
        String supplierVatNumber,
        String supplierIban,
        PaymentMeans paymentMeans,
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        List<Line> lines,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross) {

    /** How the invoice is to be paid (EN 16931 BT-81). Only a transfer needs the supplier's IBAN. */
    public enum PaymentMeans {
        /** Code 58 (SEPA) or 30: we send the money, so the supplier's IBAN is needed. */
        TRANSFER,
        /** Code 59: the supplier collects; the IBAN printed is the buyer's, on the mandate. */
        DIRECT_DEBIT,
        /** Codes 48, 54, 55. */
        CARD,
        /** Code 20. */
        CHEQUE,
        /** Code 10. */
        CASH,
        /** Marked as paid ("acquittée", "payée le..."): nothing left to pay. */
        ALREADY_PAID,
        UNKNOWN
    }

    public record Line(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate,
                       BigDecimal netAmount) {
    }

    public ExtractedInvoice {
        lines = lines == null ? List.of() : List.copyOf(lines);
        paymentMeans = paymentMeans == null ? PaymentMeans.UNKNOWN : paymentMeans;
    }

    public static ExtractedInvoice empty() {
        return new ExtractedInvoice(null, null, null, null, null, null, null, null, null, List.of(), null, null, null);
    }
}
