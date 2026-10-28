package com.mamitiana.efacture.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.MissingMentionsException;
import com.mamitiana.efacture.common.NotFoundException;
import com.mamitiana.efacture.company.CompanyRepository;
import com.mamitiana.efacture.customer.CustomerService;
import com.mamitiana.efacture.facturx.FacturXGenerator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @Mock
    private InvoiceRepository invoices;
    @Mock
    private CustomerService customers;
    @Mock
    private CompanyRepository companies;
    @Mock
    private InvoiceNumberGenerator numbers;
    @Mock
    private FacturXGenerator facturX;

    private InvoiceService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atTime(10, 0).atZone(PARIS).toInstant(), PARIS);
        service = new InvoiceService(invoices, customers, companies, numbers, new IssuanceValidator(), facturX,
                TransactionOperations.withoutTransaction(), clock);
    }

    private static Invoice draft(LocalDate dueDate) {
        return Invoice.draft(IssuanceValidatorTest.customer(), dueDate,
                List.of(new InvoiceLine("Conseil", BigDecimal.ONE, new BigDecimal("100.00"), new BigDecimal("20"))));
    }

    @Test
    void issueAssignsNumberAndDate() {
        Invoice invoice = draft(TODAY.plusDays(30));
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(companies.findSingleton()).thenReturn(Optional.of(IssuanceValidatorTest.seller()));
        when(numbers.next(InvoiceType.INVOICE, 2026)).thenReturn("F-2026-00042");

        InvoiceResponse issued = service.issue(1L);

        assertThat(issued.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(issued.number()).isEqualTo("F-2026-00042");
        assertThat(issued.issueDate()).isEqualTo(TODAY);
        assertThat(invoice.getIssuedAt()).isEqualTo(Instant.parse("2026-10-27T09:00:00Z"));
    }

    @Test
    void incompleteInvoiceIsRejectedWithoutConsumingANumber() {
        when(invoices.findById(1L)).thenReturn(Optional.of(draft(null)));
        when(companies.findSingleton()).thenReturn(Optional.of(IssuanceValidatorTest.seller()));

        assertThatThrownBy(() -> service.issue(1L))
                .isInstanceOfSatisfying(MissingMentionsException.class,
                        e -> assertThat(e.getViolations()).containsExactly("Date d'échéance"));
        verify(numbers, never()).next(any(), any(Integer.class));
    }

    @Test
    void issuedInvoiceCannotBeIssuedTwice() {
        Invoice invoice = draft(TODAY.plusDays(30));
        invoice.issue("F-2026-00001", TODAY, Instant.now());
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.issue(1L)).isInstanceOf(BusinessRuleException.class);
        verify(numbers, never()).next(any(), any(Integer.class));
    }

    @Test
    void issuedInvoiceCannotBeDeleted() {
        Invoice invoice = draft(TODAY.plusDays(30));
        invoice.issue("F-2026-00001", TODAY, Instant.now());
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.deleteDraft(1L)).isInstanceOf(BusinessRuleException.class);
        verify(invoices, never()).delete(any());
    }

    @Test
    void onlyOneCreditNotePerInvoice() {
        Invoice invoice = draft(TODAY.plusDays(30));
        invoice.issue("F-2026-00001", TODAY, Instant.now());
        when(invoices.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoices.existsByOriginalInvoiceId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.createCreditNote(1L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("F-2026-00001");
    }

    @Test
    void draftHasNoFacturX() {
        when(invoices.findById(1L)).thenReturn(Optional.of(draft(TODAY.plusDays(30))));

        assertThatThrownBy(() -> service.exportFacturX(1L)).isInstanceOf(BusinessRuleException.class);
        verify(facturX, never()).render(any());
    }

    @Test
    void unknownInvoiceIsNotFound() {
        when(invoices.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
    }
}
