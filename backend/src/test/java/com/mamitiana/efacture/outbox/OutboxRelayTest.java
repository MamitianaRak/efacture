package com.mamitiana.efacture.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {

    private final OutboxRelay relay = new OutboxRelay(null, List.of(), null, Clock.systemUTC(), 5,
            Duration.ofSeconds(10), Duration.ofMinutes(10));

    @Test
    void retryDelayDoublesUpToACap() {
        assertThat(relay.retryDelay(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(relay.retryDelay(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(relay.retryDelay(3)).isEqualTo(Duration.ofSeconds(40));
        assertThat(relay.retryDelay(10)).isEqualTo(Duration.ofMinutes(10));
        assertThat(relay.retryDelay(60)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void aMessageIsAbandonedAtItsLastAttempt() {
        OutboxMessage message = OutboxMessage.pending("TEST", 1L, java.time.Instant.EPOCH);

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(message.failed("503", java.time.Instant.EPOCH, Duration.ofSeconds(10), 5)).isFalse();
        }

        assertThat(message.failed("503", java.time.Instant.EPOCH, Duration.ofSeconds(10), 5)).isTrue();
        assertThat(message.getStatus()).isEqualTo(OutboxMessage.Status.FAILED);
        assertThat(message.getAttempts()).isEqualTo(5);
    }
}
