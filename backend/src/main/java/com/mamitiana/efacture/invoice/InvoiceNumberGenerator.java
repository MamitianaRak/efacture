package com.mamitiana.efacture.invoice;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gapless numbering. The counter row is incremented by a single atomic upsert inside the issuing transaction:
 * concurrent issuances queue on the row lock, and if the issuance rolls back, the increment rolls back too,
 * so no number is ever skipped or reused.
 */
@Component
public class InvoiceNumberGenerator {

    private final JdbcClient jdbc;

    public InvoiceNumberGenerator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(InvoiceType type, int year) {
        long value = jdbc.sql("""
                        INSERT INTO invoice_sequence (type, year, last_value) VALUES (:type, :year, 1)
                        ON CONFLICT (type, year) DO UPDATE SET last_value = invoice_sequence.last_value + 1
                        RETURNING last_value
                        """)
                .param("type", type.name())
                .param("year", year)
                .query(Long.class)
                .single();
        return "%s-%d-%05d".formatted(type.numberPrefix(), year, value);
    }
}
