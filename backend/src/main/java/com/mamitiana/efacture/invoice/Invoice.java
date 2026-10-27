package com.mamitiana.efacture.invoice;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An invoice or a credit note. State changes go through methods so the rules live in one place:
 * a draft is editable and has no number, an issued document is numbered and frozen.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private InvoiceType type;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;

    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id")
    private Invoice originalInvoice;

    private LocalDate issueDate;

    private LocalDate dueDate;

    private String currency = "EUR";

    private BigDecimal totalNet = BigDecimal.ZERO;

    private BigDecimal totalVat = BigDecimal.ZERO;

    private BigDecimal totalGross = BigDecimal.ZERO;

    private Instant createdAt = Instant.now();

    private Instant issuedAt;

    /** Optimistic lock: two concurrent edits or issuances of the same draft cannot both succeed. */
    @Version
    private long version;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<InvoiceLine> lines = new ArrayList<>();

    public static Invoice draft(Customer customer, LocalDate dueDate, List<InvoiceLine> lines) {
        Invoice invoice = new Invoice();
        invoice.type = InvoiceType.INVOICE;
        invoice.status = InvoiceStatus.DRAFT;
        invoice.customer = customer;
        invoice.dueDate = dueDate;
        invoice.replaceLines(lines);
        return invoice;
    }

    /** A credit note cancelling an issued invoice in full. It starts as a draft, like any document. */
    public static Invoice creditNoteFor(Invoice original) {
        if (original.type != InvoiceType.INVOICE || original.status == InvoiceStatus.DRAFT) {
            throw new BusinessRuleException("Un avoir ne peut annuler qu'une facture émise");
        }
        Invoice creditNote = new Invoice();
        creditNote.type = InvoiceType.CREDIT_NOTE;
        creditNote.status = InvoiceStatus.DRAFT;
        creditNote.customer = original.customer;
        creditNote.originalInvoice = original;
        creditNote.dueDate = original.dueDate;
        creditNote.replaceLines(original.lines.stream().map(InvoiceLine::copy).toList());
        return creditNote;
    }

    public void updateDraft(Customer customer, LocalDate dueDate, List<InvoiceLine> lines) {
        assertDraft();
        this.customer = customer;
        this.dueDate = dueDate;
        replaceLines(lines);
    }

    void issue(String number, LocalDate issueDate, Instant now) {
        assertDraft();
        this.number = number;
        this.issueDate = issueDate;
        this.issuedAt = now;
        this.status = InvoiceStatus.ISSUED;
    }

    public void assertDraft() {
        if (status != InvoiceStatus.DRAFT) {
            throw new BusinessRuleException(
                    "Le document " + number + " est émis : il ne peut plus être modifié, faites un avoir");
        }
    }

    public InvoiceTotals totals() {
        return InvoiceTotals.of(lines);
    }

    private void replaceLines(List<InvoiceLine> newLines) {
        lines.clear();
        int position = 1;
        for (InvoiceLine line : newLines) {
            line.setInvoice(this);
            line.setPosition(position++);
            lines.add(line);
        }
        InvoiceTotals totals = totals();
        totalNet = totals.totalNet();
        totalVat = totals.totalVat();
        totalGross = totals.totalGross();
    }
}
