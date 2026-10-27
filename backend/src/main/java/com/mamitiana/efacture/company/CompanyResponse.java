package com.mamitiana.efacture.company;

public record CompanyResponse(
        String legalName,
        String siren,
        String vatNumber,
        String street,
        String postalCode,
        String city,
        String email,
        String iban,
        String paymentTerms) {

    static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getLegalName(), c.getSiren(), c.getVatNumber(),
                c.getAddress().getStreet(), c.getAddress().getPostalCode(), c.getAddress().getCity(),
                c.getEmail(), c.getIban(), c.getPaymentTerms());
    }
}
