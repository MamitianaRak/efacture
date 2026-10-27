package com.mamitiana.efacture.platform;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One entry of a submission's status history. */
@Entity
@Table(name = "platform_submission_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubmissionEvent {

    public enum Source {
        /** Sent by our application (submission, payment). */
        SELLER,
        /** Notified by the platform (rejection, delivery, buyer decision). */
        PLATFORM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private Submission submission;

    @Enumerated(EnumType.STRING)
    private PlatformStatus status;

    private String reason;

    @Enumerated(EnumType.STRING)
    private Source source;

    private Instant occurredAt;

    SubmissionEvent(Submission submission, PlatformStatus status, String reason, Source source, Instant occurredAt) {
        this.submission = submission;
        this.status = status;
        this.reason = reason;
        this.source = source;
        this.occurredAt = occurredAt;
    }
}
