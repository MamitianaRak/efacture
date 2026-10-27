package com.mamitiana.efacture.platform;

import java.time.Instant;

/** A status update sent by the platform about one submission. */
public record PlatformNotification(String externalId, PlatformStatus status, String reason, Instant occurredAt) {
}
