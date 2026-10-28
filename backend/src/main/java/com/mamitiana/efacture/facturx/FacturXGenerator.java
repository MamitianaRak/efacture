package com.mamitiana.efacture.facturx;

import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.company.Company;
import com.mamitiana.efacture.customer.Customer;
import com.mamitiana.efacture.invoice.Invoice;
import com.mamitiana.efacture.invoice.InvoiceLine;
import com.mamitiana.efacture.invoice.InvoiceType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import org.mustangproject.BankDetails;
import org.mustangproject.Item;
import org.mustangproject.LegalOrganisation;
import org.mustangproject.Product;
import org.mustangproject.SchemedID;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromPDFA;
import org.mustangproject.ZUGFeRD.ZUGFeRDVisualizer;
import org.springframework.stereotype.Component;

/**
 * Builds a Factur-X invoice: a human-readable PDF/A-3 with the structured XML (CII, EN 16931 profile) embedded,
 * so the same file can be read by a person and imported by accounting software.
 */
@Component
public class FacturXGenerator {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** ISO 6523 scheme of the French SIREN. */
    private static final String SIREN_SCHEME = "0002";
    /** UN/ECE unit code "one" (C62). */
    private static final String UNIT_PIECE = "C62";
    private static final String COMMERCIAL_INVOICE = "380";
    private static final String CREDIT_NOTE = "381";
    private static final String SEPA_TRANSFER = "58";

    public byte[] generate(Invoice invoice, Company seller) {
        return render(prepare(invoice, seller));
    }

    /**
     * Copies what the file needs out of the entities. Fast, and the only step that reads lazy associations, so it
     * runs inside a transaction while {@link #render} runs after it, without holding a database connection.
     */
    public Prepared prepare(Invoice invoice, Company seller) {
        if (invoice.getNumber() == null) {
            throw new IllegalArgumentException("Only an issued invoice can be exported to Factur-X");
        }
        return new Prepared(invoice.getNumber(), seller.getLegalName(), toTransaction(invoice, seller));
    }

    /** Detached copy of an invoice, ready to be rendered. */
    public record Prepared(String number, String creator, org.mustangproject.Invoice transaction) {
    }

    /** The slow, CPU-bound step: XML, readable PDF (FOP) and PDF/A-3 with the XML embedded. */
    public byte[] render(Prepared prepared) {
        org.mustangproject.Invoice transaction = prepared.transaction();

        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName("EN16931"));
        provider.generateXML(transaction);
        byte[] xml = provider.getXML();

        try {
            byte[] readablePdf = renderPdf(xml);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZUGFeRDExporterFromPDFA exporter = new ZUGFeRDExporterFromPDFA()) {
                exporter.load(readablePdf);
                exporter.setProducer("eFacture PME").setCreator(prepared.creator());
                exporter.setTransaction(transaction);
                exporter.export(out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Factur-X generation failed for " + prepared.number(), e);
        }
    }

    /** Renders the XML as a human-readable PDF/A in French. */
    private static byte[] renderPdf(byte[] xml) {
        String content = new String(xml, StandardCharsets.UTF_8).replaceFirst("^﻿", "");
        byte[] pdf = new ZUGFeRDVisualizer().toPDF(content, ZUGFeRDVisualizer.Language.FR);
        if (pdf == null || pdf.length == 0) {
            throw new IllegalStateException("Mustang could not render the invoice PDF");
        }
        return pdf;
    }

    org.mustangproject.Invoice toTransaction(Invoice invoice, Company seller) {
        org.mustangproject.Invoice transaction = new org.mustangproject.Invoice()
                .setNumber(invoice.getNumber())
                .setDocumentCode(invoice.getType() == InvoiceType.CREDIT_NOTE ? CREDIT_NOTE : COMMERCIAL_INVOICE)
                .setIssueDate(date(invoice.getIssueDate()))
                .setDeliveryDate(date(invoice.getIssueDate()))
                .setDueDate(date(invoice.getDueDate()))
                .setCurrency(invoice.getCurrency())
                .setPaymentTermDescription(seller.getPaymentTerms())
                .setSender(sellerParty(seller))
                .setRecipient(buyerParty(invoice.getCustomer()));

        if (invoice.getOriginalInvoice() != null) {
            transaction.setInvoiceReferencedDocumentID(invoice.getOriginalInvoice().getNumber());
            transaction.setInvoiceReferencedIssueDate(date(invoice.getOriginalInvoice().getIssueDate()));
        }
        for (InvoiceLine line : invoice.getLines()) {
            Product product = new Product(line.getDescription(), "", UNIT_PIECE, line.getVatRate());
            transaction.addItem(new Item(product, line.getUnitPrice(), line.getQuantity()));
        }
        return transaction;
    }

    private static TradeParty sellerParty(Company seller) {
        TradeParty party = party(seller.getLegalName(), seller.getAddress(), seller.getSiren())
                .addVATID(seller.getVatNumber());
        if (seller.getEmail() != null) {
            party.setEmail(seller.getEmail());
        }
        if (seller.getIban() != null) {
            party.addBankDetails(new BankDetails(seller.getIban()).setPaymentMeansCode(SEPA_TRANSFER));
        }
        return party;
    }

    private static TradeParty buyerParty(Customer customer) {
        TradeParty party = party(customer.getLegalName(), customer.getAddress(), customer.getSiren());
        if (customer.getVatNumber() != null) {
            party.addVATID(customer.getVatNumber());
        }
        if (customer.getEmail() != null) {
            party.setEmail(customer.getEmail());
        }
        return party;
    }

    private static TradeParty party(String name, Address address, String siren) {
        TradeParty party = new TradeParty(name, address.getStreet(), address.getPostalCode(), address.getCity(),
                address.getCountryCode());
        party.setLegalOrganisation(new LegalOrganisation(new SchemedID(SIREN_SCHEME, siren), name));
        return party;
    }

    private static Date date(LocalDate date) {
        return Date.from(date.atStartOfDay(PARIS).toInstant());
    }
}
