package com.mamitiana.efacture.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the relay in the application. Disabled in tests, which call tick() themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.relay.scheduled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${app.outbox.relay.delay:PT2S}")
    void relay() {
        relay.tick();
    }
}
