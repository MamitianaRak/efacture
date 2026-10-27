package com.mamitiana.efacture.customer;

public record CustomerResponse(
        Long id,
        String legalName,
        String siren,
        String vatNumber,
        String street,
        String postalCode,
        String city,
        String email) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getLegalName(), c.getSiren(), c.getVatNumber(),
                c.getAddress().getStreet(), c.getAddress().getPostalCode(), c.getAddress().getCity(), c.getEmail());
    }
}
