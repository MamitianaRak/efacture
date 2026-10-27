package com.mamitiana.efacture;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests against a real PostgreSQL started by Testcontainers.
 * Skipped, not failed, when Docker is not running.
 */
// No real LLM call from tests, even with keys in backend/.env: tests that need a model use a scripted one.
@SpringBootTest(properties = {
        "app.platform.simulator.scheduled=false",
        "app.llm.providers.groq.api-key=",
        "app.llm.providers.gemini.api-key="})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IntegrationTest.FixedClock.class})
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /**
     * "Today" for the application and the tests alike. With the real clock, a run crossing midnight saw the test
     * compute 10 days late and the server 11: dates in assertions must come from the same clock as the server.
     */
    protected static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(10, 0).atZone(PARIS).toInstant(), PARIS);
        }
    }

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("""
                TRUNCATE invoice_line, invoice, customer, company, invoice_sequence, supplier_invoice
                RESTART IDENTITY CASCADE
                """).update();
    }
}
