package com.mamitiana.efacture.platform;

/**
 * Port to the registered platform (plateforme agréée). The application depends on this interface only:
 * the simulator can be replaced by a real platform's API client without touching the business code.
 * Status updates come back asynchronously as {@link PlatformNotification} events.
 */
public interface PlatformGateway {

    /** Hands a Factur-X file to the platform and returns the platform's identifier for it. */
    String submit(String invoiceNumber, byte[] facturX);

    /** Sends a status decided by the seller (payment received) to the platform. */
    void reportStatus(String externalId, PlatformStatus status);
}
