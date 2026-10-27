package com.mamitiana.efacture.supplier;

import com.mamitiana.efacture.ai.LlmResult;
import com.mamitiana.efacture.ai.LlmUnavailableException;
import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(SupplierInvoiceService.class);
    static final Set<String> ACCEPTED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final SupplierInvoiceRepository invoices;
    private final FacturXReader facturX;
    private final AiInvoiceReader ai;
    private final InvoiceChecks checks;
    private final Clock clock;

    public SupplierInvoiceService(SupplierInvoiceRepository invoices, FacturXReader facturX, AiInvoiceReader ai,
                                  InvoiceChecks checks, Clock clock) {
        this.invoices = invoices;
        this.facturX = facturX;
        this.ai = ai;
        this.checks = checks;
        this.clock = clock;
    }

    /**
     * Reads the file, then checks what was read. Deliberately not transactional: reading with a model takes
     * seconds, and no database connection should be held while waiting for it.
     */
    public SupplierInvoiceResponse receive(String fileName, String contentType, byte[] content, String user) {
        if (contentType == null || !ACCEPTED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Format non pris en charge : envoyez un PDF, un PNG ou un JPEG");
        }

        SupplierInvoice invoice;
        Optional<ExtractedInvoice> structured = "application/pdf".equals(contentType) ? facturX.read(content) : Optional.empty();
        if (structured.isPresent()) {
            invoice = SupplierInvoice.received(fileName, contentType, content, SupplierInvoice.Source.FACTURX, user, clock.instant());
            invoice.applyData(structured.get());
        } else {
            invoice = readWithModel(fileName, contentType, content, user);
        }

        invoice.applyChecks(runChecks(invoice, null));
        return SupplierInvoiceResponse.from(invoices.save(invoice));
    }

    private SupplierInvoice readWithModel(String fileName, String contentType, byte[] content, String user) {
        try {
            AiInvoiceReader.Reading reading = ai.read(content, contentType);
            LlmResult<ExtractedInvoice> result = reading.result();
            SupplierInvoice invoice = SupplierInvoice.received(fileName, contentType, content, SupplierInvoice.Source.AI, user, clock.instant());
            invoice.applyData(result.value());
            invoice.recordExtraction(result.provider(), result.model(), result.duration().toMillis(), reading.documentText());
            return invoice;
        } catch (LlmUnavailableException e) {
            // The file is kept: a person can still enter the invoice by hand.
            log.warn("Supplier invoice {} could not be read by a model: {}", fileName, e.getMessage());
            SupplierInvoice invoice = SupplierInvoice.received(fileName, contentType, content, SupplierInvoice.Source.MANUAL, user, clock.instant());
            invoice.applyData(ExtractedInvoice.empty());
            return invoice;
        }
    }

    @Transactional(readOnly = true)
    public List<SupplierInvoiceSummary> list() {
        return invoices.findAllByOrderByReceivedAtDesc().stream().map(SupplierInvoiceSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public SupplierInvoiceResponse get(Long id) {
        return SupplierInvoiceResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public StoredFile file(Long id) {
        SupplierInvoice invoice = load(id);
        return new StoredFile(invoice.getFileName(), invoice.getContentType(), invoice.getFileContent());
    }

    public record StoredFile(String name, String contentType, byte[] content) {
    }

    /** A person corrects what was read; every check runs again on the corrected data. */
    @Transactional
    public SupplierInvoiceResponse correct(Long id, ExtractedInvoice corrected, String user) {
        SupplierInvoice invoice = load(id);
        invoice.applyData(corrected);
        invoice.markCorrected(user, clock.instant());
        invoice.applyChecks(runChecks(invoice, id));
        return SupplierInvoiceResponse.from(invoice);
    }

    @Transactional
    public SupplierInvoiceResponse validate(Long id, String user) {
        SupplierInvoice invoice = load(id);
        invoice.validate(user, clock.instant());
        return SupplierInvoiceResponse.from(invoice);
    }

    @Transactional
    public SupplierInvoiceResponse reject(Long id, String user, String reason) {
        SupplierInvoice invoice = load(id);
        invoice.reject(user, reason, clock.instant());
        return SupplierInvoiceResponse.from(invoice);
    }

    private List<InvoiceChecks.Issue> runChecks(SupplierInvoice invoice, Long excludedId) {
        boolean duplicate = invoice.getSupplierSiren() != null && invoice.getInvoiceNumber() != null
                && invoices.existsDuplicate(invoice.getSupplierSiren(), invoice.getInvoiceNumber(), excludedId);
        return checks.check(invoice.data(), duplicate, invoice.grounding());
    }

    private SupplierInvoice load(Long id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Facture fournisseur " + id + " introuvable"));
    }
}
