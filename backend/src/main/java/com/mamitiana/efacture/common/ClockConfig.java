package com.mamitiana.efacture.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Invoice dates follow French time; injecting the clock lets tests fix "today". */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("Europe/Paris"));
    }
}
