package com.mamitiana.efacture.platform;

/**
 * Port to the registered platform (plateforme agréée). The application depends on this interface only:
 * the simulator can be replaced by a real platform's API client without touching the business code.
 * Status updates come back asynchronously as {@link PlatformNotification} events.
 *
 * Calls are made by the outbox relay, which retries after a failure, so they may arrive more than once.
 * The platform must therefore treat them idempotently.
 */
public interface PlatformGateway {

    /**
     * Hands a Factur-X file to the platform and returns the platform's identifier for it. Called again with the
     * same idempotency key (a retry after a lost answer), it returns the same identifier and stores nothing new.
     */
    String submit(String idempotencyKey, String invoiceNumber, byte[] facturX);

    /** Sends a status decided by the seller (payment received) to the platform. Repeating it changes nothing. */
    void reportStatus(String externalId, PlatformStatus status);
}
