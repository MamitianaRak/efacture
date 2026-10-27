package com.mamitiana.efacture.security;

import com.mamitiana.efacture.invoice.InvoiceRepository;
import com.mamitiana.efacture.invoice.InvoiceType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Business-aware authorization checks, used from {@code @PreAuthorize} expressions. */
@Component("invoiceAccess")
public class InvoiceAccess {

    private final InvoiceRepository invoices;

    public InvoiceAccess(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    /** The accountant may issue credit notes but not invoices. */
    @Transactional(readOnly = true)
    public boolean isCreditNote(Long invoiceId) {
        return invoices.findById(invoiceId)
                .map(invoice -> invoice.getType() == InvoiceType.CREDIT_NOTE)
                .orElse(false);
    }
}
