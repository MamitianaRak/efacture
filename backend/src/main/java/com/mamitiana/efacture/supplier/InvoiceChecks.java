package com.mamitiana.efacture.supplier;

import com.mamitiana.efacture.common.FrenchIdentifiers;
import com.mamitiana.efacture.common.Iban;
import com.mamitiana.efacture.invoice.VatRates;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic checks on what was read from a supplier invoice. A model reads the document; these rules decide
 * whether the result can be trusted. Any ERROR sends the invoice to human review.
 */
@Component
public class InvoiceChecks {

    /** One cent: amounts printed on an invoice are rounded, so exact equality is too strict. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    public enum Severity {
        ERROR,
        WARNING
    }

    public record Issue(String code, Severity severity, String message) {
    }

    public List<Issue> check(ExtractedInvoice invoice, boolean duplicate) {
        List<Issue> issues = new ArrayList<>();

        require(issues, invoice.supplierName(), "Raison sociale du fournisseur");
        require(issues, invoice.invoiceNumber(), "Numéro de facture");
        if (invoice.issueDate() == null) {
            issues.add(error("MISSING_FIELD", "Date de facture illisible ou absente"));
        }
        if (invoice.totalGross() == null) {
            issues.add(error("MISSING_FIELD", "Total TTC illisible ou absent"));
        }

        checkIdentifiers(invoice, issues);
        checkAmounts(invoice, issues);

        if (invoice.issueDate() != null && invoice.dueDate() != null && invoice.dueDate().isBefore(invoice.issueDate())) {
            issues.add(error("DUE_BEFORE_ISSUE", "L'échéance précède la date de facture"));
        }
        if (duplicate) {
            issues.add(error("DUPLICATE", "Une facture de ce fournisseur porte déjà ce numéro : doublon possible"));
        }
        return issues;
    }

    private static void checkIdentifiers(ExtractedInvoice invoice, List<Issue> issues) {
        String siren = invoice.supplierSiren();
        if (siren == null || siren.isBlank()) {
            issues.add(warning("MISSING_SIREN", "SIREN du fournisseur absent (obligatoire en facturation électronique)"));
        } else if (!FrenchIdentifiers.isValidSiren(siren)) {
            issues.add(error("INVALID_SIREN", "SIREN " + siren + " invalide : clé de contrôle incorrecte, probablement mal lu"));
        }

        String vat = invoice.supplierVatNumber();
        if (vat != null && vat.startsWith("FR")) {
            if (!FrenchIdentifiers.isValidFrenchVatNumber(vat)) {
                issues.add(error("INVALID_VAT", "Numéro de TVA " + vat + " invalide"));
            } else if (siren != null && !vat.endsWith(siren)) {
                issues.add(error("VAT_SIREN_MISMATCH", "Le numéro de TVA ne correspond pas au SIREN"));
            }
        }

        String iban = invoice.supplierIban();
        if (iban == null || iban.isBlank()) {
            issues.add(warning("MISSING_IBAN", "Coordonnées bancaires absentes"));
        } else if (!Iban.isValid(iban)) {
            issues.add(error("INVALID_IBAN", "IBAN invalide : clé de contrôle incorrecte, probablement mal lu"));
        }
    }

    private static void checkAmounts(ExtractedInvoice invoice, List<Issue> issues) {
        BigDecimal net = invoice.totalNet();
        BigDecimal vat = invoice.totalVat();
        BigDecimal gross = invoice.totalGross();
        if (net != null && vat != null && gross != null && !close(net.add(vat), gross)) {
            issues.add(error("TOTALS_MISMATCH", "HT + TVA (" + net.add(vat) + ") différent du TTC (" + gross + ")"));
        }

        List<ExtractedInvoice.Line> lines = invoice.lines();
        if (!lines.isEmpty() && net != null && lines.stream().allMatch(l -> l.netAmount() != null)) {
            BigDecimal sum = lines.stream().map(ExtractedInvoice.Line::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!close(sum, net)) {
                issues.add(error("LINES_MISMATCH", "La somme des lignes (" + sum + ") diffère du total HT (" + net + ")"));
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            ExtractedInvoice.Line line = lines.get(i);
            if (line.quantity() != null && line.unitPrice() != null && line.netAmount() != null
                    && !close(line.quantity().multiply(line.unitPrice()), line.netAmount())) {
                issues.add(warning("LINE_AMOUNT", "Ligne " + (i + 1) + " : quantité × prix différent du montant"));
            }
            if (line.vatRate() != null && !VatRates.isAllowed(line.vatRate())) {
                issues.add(warning("UNKNOWN_VAT_RATE", "Ligne " + (i + 1) + " : taux de TVA " + line.vatRate() + " % inhabituel en France"));
            }
        }
    }

    private static boolean close(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(TOLERANCE) <= 0;
    }

    private static void require(List<Issue> issues, String value, String label) {
        if (value == null || value.isBlank()) {
            issues.add(error("MISSING_FIELD", label + " illisible ou absent"));
        }
    }

    private static Issue error(String code, String message) {
        return new Issue(code, Severity.ERROR, message);
    }

    private static Issue warning(String code, String message) {
        return new Issue(code, Severity.WARNING, message);
    }
}
