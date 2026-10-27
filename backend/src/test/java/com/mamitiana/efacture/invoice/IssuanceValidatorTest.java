package com.mamitiana.efacture.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.company.Company;
import com.mamitiana.efacture.customer.Customer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IssuanceValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final IssuanceValidator validator = new IssuanceValidator();

    static Company seller() {
        Company company = new Company();
        company.setLegalName("Atelier Dupont SARL");
        company.setSiren("404833048");
        company.setVatNumber("FR83404833048");
        company.setAddress(new Address("1 rue de la Paix", "75002", "Paris", "FR"));
        company.setPaymentTerms("Paiement à 30 jours. Pénalités de retard : 3 fois le taux légal. Indemnité forfaitaire : 40 EUR.");
        return company;
    }

    static Customer customer() {
        Customer customer = new Customer();
        customer.setLegalName("Boulangerie Martin");
        customer.setSiren("552100554");
        customer.setAddress(new Address("5 place du Marché", "77000", "Melun", "FR"));
        return customer;
    }

    private static Invoice invoice(Customer customer, LocalDate dueDate, boolean withLines) {
        List<InvoiceLine> lines = withLines
                ? List.of(new InvoiceLine("Pain", BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5.5")))
                : List.of();
        return Invoice.draft(customer, dueDate, lines);
    }

    @Test
    void completeInvoiceHasNoMissingMention() {
        assertThat(validator.missingMentions(invoice(customer(), TODAY.plusDays(30), true), seller(), TODAY)).isEmpty();
    }

    @Test
    void listsEveryMissingMentionAtOnce() {
        Customer noSiren = customer();
        noSiren.setSiren(null);

        List<String> missing = validator.missingMentions(invoice(noSiren, null, false), null, TODAY);

        assertThat(missing).containsExactlyInAnyOrder(
                "Vendeur : la fiche de l'entreprise émettrice n'est pas renseignée",
                "Client : SIREN (obligatoire en facturation électronique)",
                "Date d'échéance",
                "Au moins une ligne de facture");
    }

    @Test
    void rejectsDueDateBeforeIssueDate() {
        List<String> missing = validator.missingMentions(invoice(customer(), TODAY.minusDays(1), true), seller(), TODAY);

        assertThat(missing).singleElement().asString().contains("ne peut pas précéder");
    }

    @Test
    void requiresSellerPaymentTerms() {
        Company seller = seller();
        seller.setPaymentTerms(" ");

        assertThat(validator.missingMentions(invoice(customer(), TODAY, true), seller, TODAY))
                .containsExactly("Vendeur : conditions de paiement et pénalités de retard");
    }
}
