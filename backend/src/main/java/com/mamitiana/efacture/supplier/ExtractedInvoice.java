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
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate dueDate,
        String currency,
        List<Line> lines,
        BigDecimal totalNet,
        BigDecimal totalVat,
        BigDecimal totalGross) {

    public record Line(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate,
                       BigDecimal netAmount) {
    }

    public ExtractedInvoice {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
