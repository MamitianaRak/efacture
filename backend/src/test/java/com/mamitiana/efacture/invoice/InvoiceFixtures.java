package com.mamitiana.efacture.invoice;

import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.company.Company;
import com.mamitiana.efacture.customer.Customer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Test data shared across packages. */
public final class InvoiceFixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private InvoiceFixtures() {
    }

    public static Company seller() {
        Company company = new Company();
        company.setLegalName("Atelier Dupont SARL");
        company.setSiren("404833048");
        company.setVatNumber("FR83404833048");
        company.setAddress(new Address("1 rue de la Paix", "75002", "Paris", "FR"));
        company.setEmail("facturation@atelier-dupont.fr");
        company.setIban("FR7630006000011234567890189");
        company.setPaymentTerms("Paiement à 30 jours. Pénalités de retard : 3 fois le taux d'intérêt légal. "
                + "Indemnité forfaitaire pour frais de recouvrement : 40 EUR.");
        return company;
    }

    public static Customer customer() {
        Customer customer = new Customer();
        customer.setLegalName("Boulangerie Martin");
        customer.setSiren("552100554");
        customer.setVatNumber("FR96552100554");
        customer.setAddress(new Address("5 place du Marché", "77000", "Melun", "FR"));
        return customer;
    }

    public static Invoice issuedInvoice() {
        return issuedInvoice(List.of(
                new InvoiceLine("Création du site vitrine", BigDecimal.ONE, new BigDecimal("1500.00"), new BigDecimal("20")),
                new InvoiceLine("Formation à l'outil (heures)", new BigDecimal("3"), new BigDecimal("80.00"), new BigDecimal("20")),
                new InvoiceLine("Guide imprimé", new BigDecimal("2"), new BigDecimal("12.50"), new BigDecimal("5.5"))));
    }

    public static Invoice issuedInvoice(List<InvoiceLine> lines) {
        Invoice invoice = Invoice.draft(customer(), TODAY.plusDays(30), lines);
        invoice.issue("F-2026-00001", TODAY, Instant.now());
        return invoice;
    }

    public static Invoice issuedCreditNote(Invoice original) {
        Invoice creditNote = Invoice.creditNoteFor(original);
        creditNote.issue("AV-2026-00001", TODAY, Instant.now());
        return creditNote;
    }
}
