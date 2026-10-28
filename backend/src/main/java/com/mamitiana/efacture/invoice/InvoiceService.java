package com.mamitiana.efacture.invoice;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.MissingMentionsException;
import com.mamitiana.efacture.common.NotFoundException;
import com.mamitiana.efacture.company.Company;
import com.mamitiana.efacture.company.CompanyRepository;
import com.mamitiana.efacture.customer.Customer;
import com.mamitiana.efacture.customer.CustomerService;
import com.mamitiana.efacture.facturx.FacturXGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final CustomerService customers;
    private final CompanyRepository companies;
    private final InvoiceNumberGenerator numbers;
    private final IssuanceValidator validator;
    private final FacturXGenerator facturX;
    private final TransactionOperations transaction;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, CustomerService customers, CompanyRepository companies,
                          InvoiceNumberGenerator numbers, IssuanceValidator validator, FacturXGenerator facturX,
                          TransactionOperations transaction, Clock clock) {
        this.invoices = invoices;
        this.customers = customers;
        this.companies = companies;
        this.numbers = numbers;
        this.validator = validator;
        this.facturX = facturX;
        this.transaction = transaction;
        this.clock = clock;
    }

    public List<InvoiceSummary> list(InvoiceStatus status) {
        List<Invoice> found = status == null
                ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByStatusOrderByCreatedAtDesc(status);
        return found.stream().map(InvoiceSummary::from).toList();
    }

    public InvoiceResponse get(Long id) {
        return InvoiceResponse.from(load(id));
    }

    @Transactional
    public InvoiceResponse createDraft(InvoiceRequest request) {
        Customer customer = customers.get(request.customerId());
        Invoice draft = Invoice.draft(customer, request.dueDate(), toLines(request));
        return InvoiceResponse.from(invoices.save(draft));
    }

    @Transactional
    public InvoiceResponse updateDraft(Long id, InvoiceRequest request) {
        Invoice invoice = load(id);
        invoice.updateDraft(customers.get(request.customerId()), request.dueDate(), toLines(request));
        return InvoiceResponse.from(invoice);
    }

    /** Only drafts can be deleted: they have no number, so deleting one leaves no gap in the numbering. */
    @Transactional
    public void deleteDraft(Long id) {
        Invoice invoice = load(id);
        invoice.assertDraft();
        invoices.delete(invoice);
    }

    @Transactional
    public InvoiceResponse issue(Long id) {
        Invoice invoice = load(id);
        invoice.assertDraft();

        LocalDate today = LocalDate.now(clock);
        List<String> missing = validator.missingMentions(invoice, companies.findSingleton().orElse(null), today);
        if (!missing.isEmpty()) {
            throw new MissingMentionsException(missing);
        }

        invoice.issue(numbers.next(invoice.getType(), today.getYear()), today, Instant.now(clock));
        return InvoiceResponse.from(invoice);
    }

    @Transactional
    public InvoiceResponse createCreditNote(Long invoiceId) {
        Invoice original = load(invoiceId);
        if (invoices.existsByOriginalInvoiceId(invoiceId)) {
            throw new BusinessRuleException("Un avoir existe déjà pour la facture " + original.getNumber());
        }
        return InvoiceResponse.from(invoices.save(Invoice.creditNoteFor(original)));
    }

    /**
     * The Factur-X file is rebuilt from the frozen invoice on each download, so it never drifts from the data.
     * The data is read in a short transaction and the PDF rendered after it: rendering is slow on a small CPU, and
     * holding a pooled connection meanwhile starved every other request.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public FacturXFile exportFacturX(Long id) {
        FacturXGenerator.Prepared prepared = transaction.execute(status -> {
            Invoice invoice = load(id);
            if (invoice.getStatus() == InvoiceStatus.DRAFT) {
                throw new BusinessRuleException("Un brouillon n'a pas de Factur-X : émettez d'abord la facture");
            }
            Company seller = companies.findSingleton()
                    .orElseThrow(() -> new BusinessRuleException("L'entreprise émettrice n'est pas renseignée"));
            return facturX.prepare(invoice, seller);
        });
        return new FacturXFile(prepared.number() + ".pdf", facturX.render(prepared));
    }

    public record FacturXFile(String filename, byte[] content) {
    }

    private Invoice load(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Facture " + id + " introuvable"));
    }

    private static List<InvoiceLine> toLines(InvoiceRequest request) {
        return request.lines().stream().map(InvoiceRequest.Line::toEntity).toList();
    }
}
