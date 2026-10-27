package com.mamitiana.efacture.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamitiana.efacture.IntegrationTest;
import com.mamitiana.efacture.common.Address;
import com.mamitiana.efacture.company.CompanyRepository;
import com.mamitiana.efacture.customer.Customer;
import com.mamitiana.efacture.customer.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InvoiceNumberingIntegrationTest extends IntegrationTest {

    private static final int YEAR = TODAY.getYear();

    @Autowired
    private InvoiceService service;
    @Autowired
    private InvoiceRepository invoices;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private CompanyRepository companies;

    private Long newDraft(Customer customer) {
        Invoice draft = Invoice.draft(customer, TODAY.plusDays(30),
                List.of(new InvoiceLine("Prestation", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("20"))));
        return invoices.save(draft).getId();
    }

    @Test
    void concurrentIssuancesGetConsecutiveNumbersWithoutGapsOrDuplicates() throws Exception {
        companies.save(IssuanceValidatorTest.seller());
        Customer customer = IssuanceValidatorTest.customer();
        customer.setAddress(new Address("5 place du Marché", "77000", "Melun", "FR"));
        Customer saved = customers.save(customer);
        int count = 20;
        List<Long> ids = IntStream.range(0, count).mapToObj(i -> newDraft(saved)).toList();

        List<Callable<String>> tasks = ids.stream()
                .<Callable<String>>map(id -> () -> service.issue(id).number())
                .toList();
        List<String> numbers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<String> result : pool.invokeAll(tasks)) {
                numbers.add(result.get());
            }
        }

        List<String> expected = IntStream.rangeClosed(1, count)
                .mapToObj(n -> "F-%d-%05d".formatted(YEAR, n))
                .toList();
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(expected);
    }
}
