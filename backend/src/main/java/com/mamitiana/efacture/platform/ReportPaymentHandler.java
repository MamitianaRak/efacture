package com.mamitiana.efacture.platform;

import com.mamitiana.efacture.outbox.OutboxHandler;
import com.mamitiana.efacture.outbox.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Forwards the seller's "paid" status (encaissée) to the platform. */
@Component
public class ReportPaymentHandler implements OutboxHandler {

    static final String TYPE = "REPORT_PAYMENT";
    private static final Logger log = LoggerFactory.getLogger(ReportPaymentHandler.class);

    private final SubmissionRepository submissions;
    private final PlatformGateway gateway;

    public ReportPaymentHandler(SubmissionRepository submissions, PlatformGateway gateway) {
        this.submissions = submissions;
        this.gateway = gateway;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void handle(OutboxMessage message) {
        Submission submission = submissions.findById(message.getAggregateId()).orElseThrow();
        gateway.reportStatus(submission.getExternalId(), PlatformStatus.ENCAISSEE);
    }

    @Override
    public void abandon(OutboxMessage message, String lastError) {
        // The payment stays recorded on our side; the failed message is kept in the outbox for a manual replay.
        log.error("Payment of submission {} could not be reported to the platform: {}", message.getAggregateId(), lastError);
    }
}
