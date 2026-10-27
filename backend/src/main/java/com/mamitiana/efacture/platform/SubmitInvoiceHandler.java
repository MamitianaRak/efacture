package com.mamitiana.efacture.platform;

import com.mamitiana.efacture.invoice.InvoiceService;
import com.mamitiana.efacture.outbox.OutboxHandler;
import com.mamitiana.efacture.outbox.OutboxMessage;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Sends a queued submission: builds the Factur-X file and hands it to the platform. */
@Component
public class SubmitInvoiceHandler implements OutboxHandler {

    static final String TYPE = "SUBMIT_INVOICE";

    private final SubmissionRepository submissions;
    private final InvoiceService invoices;
    private final PlatformGateway gateway;
    private final Clock clock;

    public SubmitInvoiceHandler(SubmissionRepository submissions, InvoiceService invoices, PlatformGateway gateway,
                                Clock clock) {
        this.submissions = submissions;
        this.invoices = invoices;
        this.gateway = gateway;
        this.clock = clock;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        Submission submission = submissions.findById(message.getAggregateId()).orElseThrow();
        if (submission.getStatus() != PlatformStatus.EN_COURS_ENVOI) {
            return; // already acknowledged: a duplicate message, nothing to send
        }
        InvoiceService.FacturXFile file = invoices.exportFacturX(submission.getInvoice().getId());
        // The submission id is the idempotency key: a retry after a lost answer gets the same platform id back.
        String externalId = gateway.submit("submission-" + submission.getId(), submission.getInvoice().getNumber(),
                file.content());
        submission.deposited(externalId, clock.instant());
    }

    @Override
    public void attemptFailed(OutboxMessage message, String error) {
        submissions.findById(message.getAggregateId())
                .ifPresent(submission -> submission.deliveryAttemptFailed(error, clock.instant()));
    }

    @Override
    public void abandon(OutboxMessage message, String lastError) {
        submissions.findById(message.getAggregateId())
                .filter(submission -> submission.getStatus() == PlatformStatus.EN_COURS_ENVOI)
                .ifPresent(submission -> {
                    submission.deliveryAttemptFailed(lastError, clock.instant());
                    submission.deliveryAbandoned(lastError, clock.instant());
                });
    }
}
