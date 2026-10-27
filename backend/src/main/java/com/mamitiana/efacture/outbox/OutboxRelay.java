package com.mamitiana.efacture.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends pending outbox messages, one transaction per message.
 *
 * Delivery is at least once: a message is marked sent in the same transaction as the handler's changes, so a
 * crash between the external call and the commit leads to a retry. Handlers make that retry harmless with an
 * idempotency key. Failed attempts are retried with an exponential delay, then abandoned.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 20;

    private final OutboxRepository messages;
    private final Map<String, OutboxHandler> handlers;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration firstRetryDelay;
    private final Duration maxRetryDelay;

    public OutboxRelay(OutboxRepository messages, List<OutboxHandler> handlers, PlatformTransactionManager transactions,
                       Clock clock,
                       @Value("${app.outbox.max-attempts:5}") int maxAttempts,
                       @Value("${app.outbox.first-retry-delay:PT10S}") Duration firstRetryDelay,
                       @Value("${app.outbox.max-retry-delay:PT10M}") Duration maxRetryDelay) {
        this.messages = messages;
        this.handlers = handlers.stream().collect(Collectors.toMap(OutboxHandler::type, Function.identity()));
        this.transaction = new TransactionTemplate(transactions);
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.firstRetryDelay = firstRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    /** Sends the messages due now; returns how many were processed. */
    public int tick() {
        return tickAt(clock.instant());
    }

    /** Same, as if it were {@code now}: lets tests move time forward to reach a retry. */
    public int tickAt(Instant now) {
        int processed = 0;
        while (processed < BATCH) {
            Long failedId = null;
            String error = null;
            Optional<Long> handled;
            try {
                handled = Optional.ofNullable(transaction.execute(status -> deliverNext(now)));
            } catch (DeliveryFailure failure) {
                failedId = failure.messageId;
                error = failure.getMessage();
                handled = Optional.of(failedId);
            }
            if (handled.isEmpty()) {
                break;
            }
            if (failedId != null) {
                recordFailure(failedId, error, now);
            }
            processed++;
        }
        return processed;
    }

    /** Delivers the next due message; returns its id, or null when nothing is due. */
    private Long deliverNext(Instant now) {
        OutboxMessage message = messages.lockNextDue(now).orElse(null);
        if (message == null) {
            return null;
        }
        OutboxHandler handler = handlers.get(message.getType());
        try {
            if (handler == null) {
                throw new IllegalStateException("No handler for outbox message type " + message.getType());
            }
            handler.handle(message);
            message.sent(now);
            return message.getId();
        } catch (RuntimeException e) {
            // Rolls back the handler's changes; the failure is recorded in a transaction of its own.
            throw new DeliveryFailure(message.getId(), e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }

    private void recordFailure(Long messageId, String error, Instant now) {
        transaction.executeWithoutResult(status -> messages.findById(messageId)
                .filter(m -> m.getStatus() == OutboxMessage.Status.PENDING)
                .ifPresent(message -> {
                    boolean abandoned = message.failed(error, now, retryDelay(message.getAttempts() + 1), maxAttempts);
                    OutboxHandler handler = handlers.get(message.getType());
                    if (handler != null) {
                        if (abandoned) {
                            handler.abandon(message, error);
                        } else {
                            handler.attemptFailed(message, error);
                        }
                    }
                    log.warn("Outbox message {} ({}) failed, attempt {}/{}: {}", message.getId(), message.getType(),
                            message.getAttempts(), maxAttempts, error);
                }));
    }

    /** 10 s, 20 s, 40 s... capped: a platform that is down for a while is not hammered. */
    Duration retryDelay(int attempt) {
        Duration delay = firstRetryDelay.multipliedBy(1L << Math.min(attempt - 1, 20));
        return delay.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : delay;
    }

    private static final class DeliveryFailure extends RuntimeException {
        private final Long messageId;

        DeliveryFailure(Long messageId, String message, Throwable cause) {
            super(message, cause);
            this.messageId = messageId;
        }
    }
}
