package com.mamitiana.efacture.platform;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.NotFoundException;
import com.mamitiana.efacture.company.CompanyRepository;
import com.mamitiana.efacture.invoice.Invoice;
import com.mamitiana.efacture.invoice.InvoiceRepository;
import com.mamitiana.efacture.invoice.InvoiceStatus;
import com.mamitiana.efacture.outbox.OutboxMessage;
import com.mamitiana.efacture.outbox.OutboxRepository;
import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlatformService {

    private static final Logger log = LoggerFactory.getLogger(PlatformService.class);

    private final SubmissionRepository submissions;
    private final InvoiceRepository invoices;
    private final CompanyRepository companies;
    private final OutboxRepository outbox;
    private final Clock clock;

    public PlatformService(SubmissionRepository submissions, InvoiceRepository invoices, CompanyRepository companies,
                           OutboxRepository outbox, Clock clock) {
        this.submissions = submissions;
        this.invoices = invoices;
        this.companies = companies;
        this.outbox = outbox;
        this.clock = clock;
    }

    public SubmissionResponse latest(Long invoiceId) {
        return SubmissionResponse.from(latestSubmission(invoiceId));
    }

    public Map<Long, PlatformStatus> latestStatuses() {
        return submissions.findLatestStatuses().stream().collect(Collectors.toMap(
                SubmissionRepository.LatestStatus::getInvoiceId, SubmissionRepository.LatestStatus::getStatus));
    }

    /**
     * Queues an issued invoice for the platform. The submission and the outbox message are written in this one
     * transaction, and the relay sends the file afterwards: no external call happens here, so the platform can
     * never hold a file this database does not know about, and a platform outage does not lose the request.
     *
     * A second submission is refused unless the previous one was rejected or could not be delivered; a partial
     * unique index enforces the same rule in the database against concurrent clicks.
     */
    @Transactional
    public SubmissionResponse submit(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Facture " + invoiceId + " introuvable"));
        if (invoice.getStatus() != InvoiceStatus.ISSUED) {
            throw new BusinessRuleException("Seule une facture émise peut être déposée sur la plateforme");
        }
        if (companies.findSingleton().isEmpty()) {
            throw new BusinessRuleException("L'entreprise émettrice n'est pas renseignée");
        }
        if (submissions.existsByInvoiceIdAndStatusNotIn(invoiceId, PlatformStatus.allowingResubmission())) {
            throw new BusinessRuleException("La facture " + invoice.getNumber() + " est déjà déposée sur la plateforme");
        }
        Submission submission = submissions.saveAndFlush(new Submission(invoice, clock.instant()));
        outbox.save(OutboxMessage.pending(SubmitInvoiceHandler.TYPE, submission.getId(), clock.instant()));
        return SubmissionResponse.from(submission);
    }

    /** "Encaissée" is declared by the seller, recorded now, and forwarded to the platform through the outbox. */
    @Transactional
    public SubmissionResponse markPaid(Long invoiceId) {
        Submission submission = latestSubmission(invoiceId);
        if (submission.getStatus() != PlatformStatus.MISE_A_DISPOSITION
                && submission.getStatus() != PlatformStatus.APPROUVEE) {
            throw new BusinessRuleException("Une facture " + label(submission.getStatus()) + " ne peut pas être encaissée");
        }
        submission.apply(PlatformStatus.ENCAISSEE, null, SubmissionEvent.Source.SELLER, clock.instant());
        outbox.save(OutboxMessage.pending(ReportPaymentHandler.TYPE, submission.getId(), clock.instant()));
        return SubmissionResponse.from(submission);
    }

    /** Status updates from the platform. Replaying the same notification changes nothing. */
    @EventListener
    @Transactional
    public void onNotification(PlatformNotification notification) {
        Submission submission = submissions.findByExternalId(notification.externalId())
                .orElseThrow(() -> new NotFoundException("Dépôt inconnu : " + notification.externalId()));
        boolean changed = submission.apply(notification.status(), notification.reason(),
                SubmissionEvent.Source.PLATFORM, notification.occurredAt());
        if (changed) {
            log.info("Invoice {} is now {} on the platform", submission.getInvoice().getNumber(), notification.status());
        }
    }

    private Submission latestSubmission(Long invoiceId) {
        return submissions.findFirstByInvoiceIdOrderBySubmittedAtDescIdDesc(invoiceId)
                .orElseThrow(() -> new NotFoundException("Cette facture n'a pas encore été déposée sur la plateforme"));
    }

    private static String label(PlatformStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }
}
