package com.mamitiana.efacture.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A message to an external system, recorded in the same transaction as the change that requires it. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    public enum Status {
        PENDING,
        SENT,
        /** Every attempt failed: kept for inspection, never retried automatically. */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What to do, e.g. SUBMIT_INVOICE; a handler is registered for each type. */
    private String type;

    /** The entity the message is about (a submission id, for the platform). */
    private Long aggregateId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int attempts;

    private Instant nextAttemptAt;

    private String lastError;

    private Instant createdAt;

    private Instant sentAt;

    public static OutboxMessage pending(String type, Long aggregateId, Instant now) {
        OutboxMessage message = new OutboxMessage();
        message.type = type;
        message.aggregateId = aggregateId;
        message.status = Status.PENDING;
        message.nextAttemptAt = now;
        message.createdAt = now;
        return message;
    }

    void sent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        lastError = null;
    }

    /** Records a failed attempt; returns true when the message has used up its attempts. */
    boolean failed(String error, Instant now, Duration retryDelay, int maxAttempts) {
        attempts++;
        lastError = error.length() > 1000 ? error.substring(0, 1000) : error;
        if (attempts >= maxAttempts) {
            status = Status.FAILED;
            return true;
        }
        nextAttemptAt = now.plus(retryDelay);
        return false;
    }
}
