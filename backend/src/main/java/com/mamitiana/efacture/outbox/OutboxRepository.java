package com.mamitiana.efacture.outbox;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    /**
     * Locks the oldest due message. SKIP LOCKED makes concurrent relays (several instances, or a slow tick
     * overlapping the next one) each take a different message instead of waiting or sending the same one twice.
     */
    @Query(value = """
            SELECT * FROM outbox_message
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<OutboxMessage> lockNextDue(Instant now);

    long countByStatus(OutboxMessage.Status status);
}
