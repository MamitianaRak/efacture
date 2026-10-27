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

    @Version
    private long version;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt, id")
    private List<SubmissionEvent> events = new ArrayList<>();

    public Submission(Invoice invoice, String externalId, Instant now) {
        this.invoice = invoice;
        this.externalId = externalId;
        this.status = PlatformStatus.DEPOSEE;
        this.submittedAt = now;
        this.updatedAt = now;
        events.add(new SubmissionEvent(this, PlatformStatus.DEPOSEE, null, SubmissionEvent.Source.SELLER, now));
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
