package com.mamitiana.efacture.customer;

import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    public List<Customer> findAll() {
        return customers.findAllByOrderByLegalNameAsc();
    }

    public Customer get(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Client " + id + " introuvable"));
    }

    @Transactional
    public Customer create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return customers.save(customer);
    }

    @Transactional
    public Customer update(Long id, CustomerRequest request) {
        Customer customer = get(id);
        apply(customer, request);
        return customer;
    }

    private static void apply(Customer customer, CustomerRequest request) {
        if (request.siren() != null && request.vatNumber() != null && !request.vatNumber().endsWith(request.siren())) {
            throw new BusinessRuleException("Le numéro de TVA ne correspond pas au SIREN du client");
        }
        customer.setLegalName(request.legalName());
        customer.setSiren(request.siren());
        customer.setVatNumber(request.vatNumber());
        customer.setAddress(new Address(request.street(), request.postalCode(), request.city(), "FR"));
        customer.setEmail(request.email());
    }
}
