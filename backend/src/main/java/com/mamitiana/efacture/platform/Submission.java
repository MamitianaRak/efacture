package com.mamitiana.efacture.platform;

import com.mamitiana.efacture.common.BusinessRuleException;
import com.mamitiana.efacture.invoice.Invoice;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One submission of an issued invoice to the platform, with its status history. */
@Entity
@Table(name = "platform_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /** Identifier given by the platform; status updates refer to it. */
    private String externalId;

    @Enumerated(EnumType.STRING)
    private PlatformStatus status;

    private String statusReason;

    private Instant submittedAt;

    private Instant updatedAt;

    /** Failed attempts to hand the file to the platform, and the last error, shown while retrying. */
    private int deliveryAttempts;

    private String lastDeliveryError;

    @Version
    private long version;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt, id")
    private List<SubmissionEvent> events = new ArrayList<>();

    /** A submission is born queued: the outbox relay sends it, and the platform's id arrives with the answer. */
    public Submission(Invoice invoice, Instant now) {
        this.invoice = invoice;
        this.status = PlatformStatus.EN_COURS_ENVOI;
        this.submittedAt = now;
        this.updatedAt = now;
        events.add(new SubmissionEvent(this, PlatformStatus.EN_COURS_ENVOI, null, SubmissionEvent.Source.SELLER, now));
    }

    /** The platform acknowledged the file and gave it an id. */
    public void deposited(String externalId, Instant at) {
        this.externalId = externalId;
        this.lastDeliveryError = null;
        apply(PlatformStatus.DEPOSEE, null, SubmissionEvent.Source.PLATFORM, at);
    }

    /** One more failed attempt; the outbox will retry. */
    public void deliveryAttemptFailed(String error, Instant at) {
        this.deliveryAttempts++;
        this.lastDeliveryError = error;
        this.updatedAt = at;
    }

    /** Every retry failed: the invoice is free to be submitted again. */
    public void deliveryAbandoned(String error, Instant at) {
        this.lastDeliveryError = error;
        apply(PlatformStatus.ECHEC_ENVOI, "Plateforme injoignable après plusieurs tentatives : " + error,
                SubmissionEvent.Source.SELLER, at);
    }

    /**
     * Applies a status update. Returns false when the submission already has this status: platforms may
     * deliver the same notification twice, and replaying it must change nothing.
     */
    public boolean apply(PlatformStatus next, String reason, SubmissionEvent.Source source, Instant at) {
        if (next == status) {
            return false;
        }
        if (!status.canMoveTo(next)) {
            throw new BusinessRuleException("Statut " + status + " : passage à " + next + " impossible");
        }
        status = next;
        statusReason = reason;
        updatedAt = at;
        events.add(new SubmissionEvent(this, next, reason, source, at));
        return true;
    }
}
