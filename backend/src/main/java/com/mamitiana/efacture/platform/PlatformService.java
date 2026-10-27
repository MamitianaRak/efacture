package com.mamitiana.efacture.platform;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.common.NotFoundException;
import com.mamitiana.efacture.invoice.Invoice;
import com.mamitiana.efacture.invoice.InvoiceRepository;
import com.mamitiana.efacture.invoice.InvoiceService;
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
    private final InvoiceService invoiceService;
    private final PlatformGateway gateway;
    private final Clock clock;

    public PlatformService(SubmissionRepository submissions, InvoiceRepository invoices, InvoiceService invoiceService,
                           PlatformGateway gateway, Clock clock) {
        this.submissions = submissions;
        this.invoices = invoices;
        this.invoiceService = invoiceService;
        this.gateway = gateway;
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
     * Submits an issued invoice. A second submission is refused unless the previous one was rejected;
     * a partial unique index enforces the same rule in the database against concurrent clicks.
     */
    @Transactional
    public SubmissionResponse submit(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId)
                .orElseThrow(() -> new NotFoundException("Facture " + invoiceId + " introuvable"));
        if (submissions.existsByInvoiceIdAndStatusNot(invoiceId, PlatformStatus.REJETEE)) {
            throw new BusinessRuleException("La facture " + invoice.getNumber() + " est déjà déposée sur la plateforme");
        }
        InvoiceService.FacturXFile file = invoiceService.exportFacturX(invoiceId);
        String externalId = gateway.submit(invoice.getNumber(), file.content());
        Submission submission = submissions.saveAndFlush(new Submission(invoice, externalId, clock.instant()));
        return SubmissionResponse.from(submission);
    }

    /** "Encaissée" is declared by the seller, then forwarded to the platform. */
    @Transactional
    public SubmissionResponse markPaid(Long invoiceId) {
        Submission submission = latestSubmission(invoiceId);
        if (submission.getStatus() != PlatformStatus.MISE_A_DISPOSITION
                && submission.getStatus() != PlatformStatus.APPROUVEE) {
            throw new BusinessRuleException("Une facture " + label(submission.getStatus()) + " ne peut pas être encaissée");
        }
        submission.apply(PlatformStatus.ENCAISSEE, null, SubmissionEvent.Source.SELLER, clock.instant());
        gateway.reportStatus(submission.getExternalId(), PlatformStatus.ENCAISSEE);
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
