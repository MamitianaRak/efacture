package com.mamitiana.efacture.platform;

import java.time.Instant;
import java.util.List;

public record SubmissionResponse(
        String externalId,
        PlatformStatus status,
        String statusReason,
        boolean finalStatus,
        Instant submittedAt,
        Instant updatedAt,
        List<Event> events,
        boolean simulated) {

    public record Event(PlatformStatus status, String reason, SubmissionEvent.Source source, Instant occurredAt) {
    }

    static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(s.getExternalId(), s.getStatus(), s.getStatusReason(), s.getStatus().isFinal(),
                s.getSubmittedAt(), s.getUpdatedAt(),
                s.getEvents().stream()
                        .map(e -> new Event(e.getStatus(), e.getReason(), e.getSource(), e.getOccurredAt()))
                        .toList(),
                true);
    }
}
