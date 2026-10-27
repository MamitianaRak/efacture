package com.mamitiana.efacture.outbox;

/**
 * Delivers one type of outbox message. Called inside a transaction that also marks the message sent: if
 * {@link #handle} throws, every change it made is rolled back and the message is retried later. Since a retry
 * may follow a call the external system did receive, handlers must call it idempotently.
 */
public interface OutboxHandler {

    String type();

    void handle(OutboxMessage message);

    /** Called once the message has used up its attempts. */
    void abandon(OutboxMessage message, String lastError);

    /** Called after each failed attempt that will be retried, to show progress to users. */
    default void attemptFailed(OutboxMessage message, String error) {
    }
}
