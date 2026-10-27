package com.mamitiana.efacture;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcClient jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("TRUNCATE invoice_line, invoice, customer, company, invoice_sequence RESTART IDENTITY CASCADE").update();
    }
}
