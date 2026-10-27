package com.mamitiana.efacture.supplier;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.mustangproject.BankDetails;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDLegalOrganisation;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the structured XML embedded in a Factur-X PDF. No model involved: when the supplier already sends
 * a Factur-X file, its data is exact and nothing needs to be guessed.
 */
@Component
public class FacturXReader {

    private static final Logger log = LoggerFactory.getLogger(FacturXReader.class);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** Empty when the PDF carries no Factur-X XML, i.e. it is a plain PDF or a scan. */
    public Optional<ExtractedInvoice> read(byte[] pdf) {
        try {
            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter(new ByteArrayInputStream(pdf));
            if (!importer.canParse()) {
                return Optional.empty();
            }
            return Optional.of(toExtracted(importer.extractInvoice()));
        } catch (Exception e) {
            // A PDF with no or broken XML is not an error here: the model will read it instead.
            log.debug("No readable Factur-X XML: {}", e.toString());
            return Optional.empty();
        }
    }

    private static ExtractedInvoice toExtracted(Invoice invoice) {
        TradeParty seller = invoice.getSender();
        List<ExtractedInvoice.Line> lines = Arrays.stream(invoice.getZFItems())
                .map(Item.class::cast)
                .map(item -> new ExtractedInvoice.Line(item.getProduct().getName(), item.getQuantity(), item.getPrice(),
                        item.getProduct().getVATPercent(),
                        item.getQuantity().multiply(item.getPrice()).setScale(2, RoundingMode.HALF_UP)))
                .toList();

        TransactionCalculator totals = new TransactionCalculator(invoice);
        BigDecimal net = totals.getTaxBasis().setScale(2, RoundingMode.HALF_UP);
        BigDecimal gross = totals.getGrandTotal().setScale(2, RoundingMode.HALF_UP);

        BankDetails bank = seller.getBankDetails() == null ? null
                : seller.getBankDetails().stream().findFirst().orElse(null);

        return new ExtractedInvoice(
                seller.getName(),
                siren(seller.getLegalOrganisation()),
                seller.getVATID(),
                bank == null ? null : bank.getIBAN(),
                paymentMeans(bank),
                invoice.getNumber(),
                date(invoice.getIssueDate()),
                date(invoice.getDueDate()),
                invoice.getCurrency(),
                lines,
                net,
                gross.subtract(net),
                gross);
    }

    /** UNTDID 4461 payment means codes, as written in the Factur-X XML. */
    private static ExtractedInvoice.PaymentMeans paymentMeans(BankDetails bank) {
        if (bank == null) {
            return ExtractedInvoice.PaymentMeans.UNKNOWN;
        }
        String code = bank.getPaymentMeansCode();
        if (code == null) {
            // Bank details without a code: they are given to be paid by transfer.
            return bank.getIBAN() != null ? ExtractedInvoice.PaymentMeans.TRANSFER : ExtractedInvoice.PaymentMeans.UNKNOWN;
        }
        return switch (code) {
            case "30", "58" -> ExtractedInvoice.PaymentMeans.TRANSFER;
            case "59" -> ExtractedInvoice.PaymentMeans.DIRECT_DEBIT;
            case "48", "54", "55" -> ExtractedInvoice.PaymentMeans.CARD;
            case "20" -> ExtractedInvoice.PaymentMeans.CHEQUE;
            case "10" -> ExtractedInvoice.PaymentMeans.CASH;
            default -> ExtractedInvoice.PaymentMeans.UNKNOWN;
        };
    }

    private static String siren(IZUGFeRDLegalOrganisation organisation) {
        if (organisation == null || organisation.getSchemedID() == null) {
            return null;
        }
        return organisation.getSchemedID().getID();
    }

    private static LocalDate date(Date date) {
        return date == null ? null : date.toInstant().atZone(PARIS).toLocalDate();
    }
}
