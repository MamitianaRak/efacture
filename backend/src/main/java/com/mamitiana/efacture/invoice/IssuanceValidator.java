package com.mamitiana.efacture.invoice;

import com.mamitiana.efacture.company.Company;
import com.mamitiana.efacture.customer.Customer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Lists the legal mentions missing before an invoice can be issued. An empty list means it can be issued. */
@Component
public class IssuanceValidator {

    public List<String> missingMentions(Invoice invoice, Company seller, LocalDate issueDate) {
        List<String> missing = new ArrayList<>();

        if (seller == null) {
            missing.add("Vendeur : la fiche de l'entreprise émettrice n'est pas renseignée");
        } else {
            requireText(missing, seller.getLegalName(), "Vendeur : raison sociale");
            requireText(missing, seller.getSiren(), "Vendeur : SIREN");
            requireText(missing, seller.getVatNumber(), "Vendeur : numéro de TVA intracommunautaire");
            requireText(missing, seller.getPaymentTerms(), "Vendeur : conditions de paiement et pénalités de retard");
        }

        Customer customer = invoice.getCustomer();
        requireText(missing, customer.getLegalName(), "Client : raison sociale");
        requireText(missing, customer.getSiren(), "Client : SIREN (obligatoire en facturation électronique)");
        if (customer.getAddress() == null || isBlank(customer.getAddress().getStreet())) {
            missing.add("Client : adresse");
        }

        if (invoice.getDueDate() == null) {
            missing.add("Date d'échéance");
        } else if (invoice.getDueDate().isBefore(issueDate)) {
            missing.add("Date d'échéance : elle ne peut pas précéder la date d'émission");
        }
        if (invoice.getLines().isEmpty()) {
            missing.add("Au moins une ligne de facture");
        }
        return missing;
    }

    private static void requireText(List<String> missing, String value, String label) {
        if (isBlank(value)) {
            missing.add(label);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
