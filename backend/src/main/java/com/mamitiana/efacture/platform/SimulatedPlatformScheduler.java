package com.mamitiana.efacture.platform;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Drives the simulator in the running application. Disabled in tests, which call tick() themselves. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.platform.simulator.scheduled", havingValue = "true", matchIfMissing = true)
public class SimulatedPlatformScheduler {

    private final SimulatedPlatform platform;

    public SimulatedPlatformScheduler(SimulatedPlatform platform) {
        this.platform = platform;
    }

    @Scheduled(fixedDelayString = "${app.platform.simulator.delay:PT5S}")
    void advance() {
        platform.tick();
    }
}
