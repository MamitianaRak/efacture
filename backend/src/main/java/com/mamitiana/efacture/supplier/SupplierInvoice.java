package com.mamitiana.efacture.supplier;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.Iban;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
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
 * An invoice received from a supplier. What was read from the file can be corrected by a person until the
 * invoice is validated; validation is refused while a blocking check fails.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierInvoice {

    public enum Status {
        /** At least one blocking check failed: a person must look at it. */
        NEEDS_REVIEW,
        /** Every blocking check passed; waiting for a person's validation. */
        CHECKED,
        VALIDATED,
        REJECTED
    }

    public enum Source {
        /** Data read from the Factur-X XML: exact. */
        FACTURX,
        /** Data read by a model from a plain PDF or an image. */
        AI,
        /** No model could read the file: entered by hand. */
        MANUAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Source source;

    private String fileName;

    private String contentType;

    @Column(name = "file_content")
    private byte[] fileContent;

    private String supplierName;
    private String supplierSiren;
    private String supplierVatNumber;
    private String supplierIban;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currency;
    private BigDecimal totalNet;
    private BigDecimal totalVat;
    private BigDecimal totalGross;

    private String extractionProvider;
    private String extractionModel;
    private Long extractionMs;

    private String receivedBy;
    private Instant receivedAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String rejectionReason;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "supplier_invoice_line", joinColumns = @JoinColumn(name = "supplier_invoice_id"))
    @OrderColumn(name = "position")
    private List<LineValue> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "supplier_invoice_issue", joinColumns = @JoinColumn(name = "supplier_invoice_id"))
    private List<IssueValue> issues = new ArrayList<>();

    public static SupplierInvoice received(String fileName, String contentType, byte[] content, Source source,
                                           String receivedBy, Instant now) {
        SupplierInvoice invoice = new SupplierInvoice();
        invoice.fileName = fileName;
        invoice.contentType = contentType;
        invoice.fileContent = content;
        invoice.source = source;
        invoice.receivedBy = receivedBy;
        invoice.receivedAt = now;
        invoice.status = Status.NEEDS_REVIEW;
        return invoice;
    }

    public void recordExtraction(String provider, String model, long durationMs) {
        this.extractionProvider = provider;
        this.extractionModel = model;
        this.extractionMs = durationMs;
    }

    /** Replaces the data with what was read, or with a person's corrections. */
    public void applyData(ExtractedInvoice data) {
        assertOpen();
        supplierName = data.supplierName();
        supplierSiren = data.supplierSiren();
        supplierVatNumber = data.supplierVatNumber();
        supplierIban = Iban.normalize(data.supplierIban());
        invoiceNumber = data.invoiceNumber();
        issueDate = data.issueDate();
        dueDate = data.dueDate();
        currency = data.currency() == null ? "EUR" : data.currency();
        totalNet = data.totalNet();
        totalVat = data.totalVat();
        totalGross = data.totalGross();
        lines.clear();
        data.lines().forEach(l -> lines.add(new LineValue(l.description(), l.quantity(), l.unitPrice(), l.vatRate(), l.netAmount())));
    }

    public void applyChecks(List<InvoiceChecks.Issue> found) {
        assertOpen();
        issues.clear();
        found.forEach(i -> issues.add(new IssueValue(i.code(), i.severity(), i.message())));
        status = hasBlockingIssue() ? Status.NEEDS_REVIEW : Status.CHECKED;
    }

    public void validate(String by, Instant now) {
        assertOpen();
        if (hasBlockingIssue()) {
            throw new BusinessRuleException("Corrigez les erreurs avant de valider cette facture");
        }
        status = Status.VALIDATED;
        reviewedBy = by;
        reviewedAt = now;
    }

    public void reject(String by, String reason, Instant now) {
        assertOpen();
        status = Status.REJECTED;
        rejectionReason = reason;
        reviewedBy = by;
        reviewedAt = now;
    }

    public ExtractedInvoice data() {
        return new ExtractedInvoice(supplierName, supplierSiren, supplierVatNumber, supplierIban, invoiceNumber,
                issueDate, dueDate, currency,
                lines.stream().map(l -> new ExtractedInvoice.Line(l.getDescription(), l.getQuantity(), l.getUnitPrice(),
                        l.getVatRate(), l.getNetAmount())).toList(),
                totalNet, totalVat, totalGross);
    }

    private boolean hasBlockingIssue() {
        return issues.stream().anyMatch(i -> i.getSeverity() == InvoiceChecks.Severity.ERROR);
    }

    private void assertOpen() {
        if (status == Status.VALIDATED || status == Status.REJECTED) {
            throw new BusinessRuleException("Cette facture fournisseur est déjà traitée");
        }
    }
}
