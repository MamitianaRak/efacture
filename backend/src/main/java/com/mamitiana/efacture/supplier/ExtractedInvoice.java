package com.mamitiana.efacture.supplier;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What was read from a supplier invoice, whether from the Factur-X XML or by a model. This record is also the
 * JSON schema the model must fill: every field it cannot read on the document must stay null.
 *
 * The descriptions are part of that schema. Without them, a lighter model left "totalGross" empty on 15 invoices
 * out of 18: nothing told it that this English name meant the "Total TTC" printed on a French invoice.
 */
public record ExtractedInvoice(
        @JsonPropertyDescription("Raison sociale du fournisseur, l'émetteur de la facture")
        String supplierName,
        @JsonPropertyDescription("SIREN du fournisseur : 9 chiffres (les 9 premiers chiffres du SIRET)")
        String supplierSiren,
        @JsonPropertyDescription("Numéro de TVA intracommunautaire du fournisseur, sans espaces")
        String supplierVatNumber,
        @JsonPropertyDescription("IBAN du fournisseur donné pour le payer, sans espaces ; null s'il n'est pas imprimé")
        String supplierIban,
        @JsonPropertyDescription("Moyen de paiement prévu")
        PaymentMeans paymentMeans,
        @JsonPropertyDescription("Numéro de la facture, tel qu'imprimé")
        String invoiceNumber,
        @JsonPropertyDescription("Date de la facture (date d'émission)")
        LocalDate issueDate,
        @JsonPropertyDescription("Date d'échéance : date limite de paiement imprimée ; null si absente")
        LocalDate dueDate,
        @JsonPropertyDescription("Devise, code ISO (EUR)")
        String currency,
        @JsonPropertyDescription("Lignes de la facture, dans l'ordre")
        List<Line> lines,
        @JsonPropertyDescription("Total HT : montant total hors taxes")
        BigDecimal totalNet,
        @JsonPropertyDescription("Montant total de la TVA")
        BigDecimal totalVat,
        @JsonPropertyDescription("Total TTC : montant toutes taxes comprises, à payer")
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

    public record Line(
            @JsonPropertyDescription("Désignation de la ligne")
            String description,
            @JsonPropertyDescription("Quantité ; null si non imprimée")
            BigDecimal quantity,
            @JsonPropertyDescription("Prix unitaire hors taxes ; null si non imprimé")
            BigDecimal unitPrice,
            @JsonPropertyDescription("Taux de TVA en pourcentage (20 pour 20 %)")
            BigDecimal vatRate,
            @JsonPropertyDescription("Montant hors taxes de la ligne")
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
